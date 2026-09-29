package atm.system;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Single place where the whole app talks to the database.
 *
 * <p>Every screen does {@code Conn c = new Conn(); c.s.execute...} exactly as before, so
 * no other class in this package had to change. What changed is <b>where</b> the connection
 * points: it is now configurable, so the same jar runs against a free cloud MySQL
 * (TiDB Cloud Serverless / Aiven / PlanetScale-style MySQL) with no recompiling.
 *
 * <p>Configuration is resolved in this order, first match wins:
 * <ol>
 *   <li>JVM system properties: {@code -Datm.db.url=... -Datm.db.user=... -Datm.db.pass=...}</li>
 *   <li>Environment variables: {@code ATM_DB_URL}, {@code ATM_DB_USER}, {@code ATM_DB_PASS},
 *       or {@code ATM_DB_HOST} / {@code ATM_DB_PORT} / {@code ATM_DB_NAME} / {@code ATM_DB_PARAMS}</li>
 *   <li>File {@code atm-db.properties} in the working directory or next to the jar</li>
 *   <li>Local MySQL defaults, so the app still runs on a laptop with zero setup</li>
 * </ol>
 *
 * <p>Run {@code java -cp atm-system.jar atm.system.Conn} as a headless connectivity test
 * (it prints a status line and exits) - handy for checking a free cloud instance without
 * opening the GUI.
 */
public class Conn {

    public static final String DEFAULT_HOST = "localhost";
    public static final String DEFAULT_PORT = "3306";
    public static final String DEFAULT_NAME = "bankmanagementsystem_db";
    public static final String DEFAULT_USER = "root";
    /**
     * Kept so existing callers still compile, but deliberately empty: a password in source is
     * published to everyone who clones the repository. Put the real one in atm-db.properties.
     */
    public static final String DEFAULT_PASS = "";

    /** Tables the app needs; created on connect so a brand new cloud database just works. */
    private static final String[] DDL = {
        "CREATE TABLE IF NOT EXISTS login ("
            + "cardno VARCHAR(16) NOT NULL PRIMARY KEY, "
            + "pin VARCHAR(16) NOT NULL)",
        "CREATE TABLE IF NOT EXISTS signup ("
            + "formno VARCHAR(16) NOT NULL PRIMARY KEY, "
            + "name VARCHAR(40), fname VARCHAR(40), dob VARCHAR(40), gender VARCHAR(16), "
            + "email VARCHAR(60), marital VARCHAR(20), address VARCHAR(120), city VARCHAR(40))",
        "CREATE TABLE IF NOT EXISTS signup2 ("
            + "formno VARCHAR(16) NOT NULL PRIMARY KEY, "
            + "religion VARCHAR(20), category VARCHAR(20), income VARCHAR(30), education VARCHAR(20), "
            + "occupation VARCHAR(20), pan VARCHAR(30), aadhar VARCHAR(30), "
            + "scitizen VARCHAR(10), eaccount VARCHAR(10))",
        "CREATE TABLE IF NOT EXISTS signupthree ("
            + "formno VARCHAR(16) NOT NULL PRIMARY KEY, "
            + "acctype VARCHAR(30), services VARCHAR(200), e_account VARCHAR(10), pin VARCHAR(16))",
        "CREATE TABLE IF NOT EXISTS bank ("
            + "pin VARCHAR(16), date VARCHAR(80), mode VARCHAR(20), amount VARCHAR(40))"
    };

    private static final Properties FILE_PROPS = loadPropertiesFile();
    private static final Object LOCK = new Object();
    private static Connection shared;
    private static String sharedTo;
    private static boolean schemaChecked = false;

    /** Same fields, same visibility as before - do not change, other classes use them. */
    Connection c;
    Statement s;

    public Conn() {
        try {
            // Load the MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            String db = config("db.name", "ATM_DB_NAME", DEFAULT_NAME);
            String user = config("db.user", "ATM_DB_USER", DEFAULT_USER);
            String pass = config("db.pass", "ATM_DB_PASS", DEFAULT_PASS);
            String url = fullUrl(db);

            c = liveConnection(url, user, pass, db);
            s = c.createStatement();

            ensureSchema();

            if (isFirst) {
                System.out.println("Database connected successfully! -> " + hostOf(url) + "/" + db);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isFirst = false;

    /**
     * The screens build a {@code new Conn()} on every click and never close it. On a cloud
     * database that would leak one connection per click and eventually hit the provider's
     * connection limit, so all of them share one connection which is re-opened if the cloud
     * side dropped it while idle. Same {@code c} / {@code s} usage as before.
     */
    private Connection liveConnection(String url, String user, String pass, String db)
            throws SQLException {
        synchronized (LOCK) {
            if (shared == null) {
                isFirst = true;
            } else if (sharedTo.equals(url) && isAlive(shared)) {
                return shared;
            } else {
                closeQuietly(shared);
                shared = null;
            }
            if (shared == null) {
                shared = open(url, user, pass, db);
                sharedTo = url;
            }
            return shared;
        }
    }

    private static boolean isAlive(Connection conn) {
        try {
            return !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void closeQuietly(Connection conn) {
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
    }

    /**
     * Connects, and if the schema/database does not exist yet (normal on a fresh free
     * cloud instance) creates it and retries once.
     */
    private Connection open(String url, String user, String pass, String db) throws SQLException {
        try {
            return DriverManager.getConnection(url, user, pass);
        } catch (SQLException first) {
            if (first.getErrorCode() != 1049 || db == null || db.isEmpty()) {
                throw first;
            }
            // 1049 = Unknown database: create it on the same server, then reconnect.
            Connection root = null;
            try {
                root = DriverManager.getConnection(urlWithoutDatabase(url), user, pass);
                Statement st = root.createStatement();
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + db + "`");
                st.close();
                return DriverManager.getConnection(url, user, pass);
            } finally {
                if (root != null) {
                    try {
                        root.close();
                    } catch (SQLException ignored) {
                    }
                }
            }
        }
    }

    /** Idempotent: only ever runs CREATE ... IF NOT EXISTS, once per JVM. */
    private void ensureSchema() {
        if (schemaChecked) {
            return;
        }
        try {
            for (String ddl : DDL) {
                s.executeUpdate(ddl);
            }
            alignBankColumn();
            schemaChecked = true;
        } catch (SQLException e) {
            System.err.println("Schema check skipped: " + e.getMessage());
        }
    }

    /**
     * Old copies of this project (and some tutorials) named the third bank column
     * {@code type} while every screen reads {@code mode}. Rename it once so the whole app
     * agrees on one name. Data is preserved, and it is skipped when the column is already
     * {@code mode}.
     */
    private void alignBankColumn() {
        try {
            String legacyType = columnType("bank", "type");
            String currentType = columnType("bank", "mode");
            if (legacyType != null && currentType == null) {
                // CHANGE COLUMN (not RENAME) so the same statement works on MySQL and TiDB
                s.executeUpdate("ALTER TABLE bank CHANGE COLUMN type mode " + legacyType);
                System.out.println("Schema upgraded: bank.type renamed to bank.mode");
            }
        } catch (SQLException e) {
            System.err.println("bank column check skipped: " + e.getMessage());
        }
    }

    /** Column definition (e.g. "varchar(20)") or null when the column does not exist. */
    private String columnType(String table, String column) throws SQLException {
        try (ResultSet rs = s.executeQuery(
                "SELECT column_type FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' "
                    + "AND column_name = '" + column + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    // ------------------------------------------------------------------ config

    private static String fullUrl(String db) {
        String explicit = config("db.url", "ATM_DB_URL", null);
        if (explicit != null) {
            return explicit;
        }
        String host = config("db.host", "ATM_DB_HOST", DEFAULT_HOST);
        String port = config("db.port", "ATM_DB_PORT", DEFAULT_PORT);
        String params = config("db.params", "ATM_DB_PARAMS",
            "sslMode=PREFERRED&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                + "&connectTimeout=15000&socketTimeout=30000&useUnicode=true&characterEncoding=UTF-8");
        return "jdbc:mysql://" + host + ":" + port + "/" + db + "?" + params;
    }

    private static String urlWithoutDatabase(String url) {
        int q = url.indexOf('?');
        String base = q < 0 ? url : url.substring(0, q);
        String params = q < 0 ? "" : url.substring(q);
        int slash = base.lastIndexOf('/');
        return slash < 0 ? base : base.substring(0, slash) + "/" + params;
    }

    private static String hostOf(String url) {
        int q = url.indexOf('?');
        String base = q < 0 ? url : url.substring(0, q);
        int slash = base.lastIndexOf('/');
        return slash < 0 ? base : base.substring(0, slash);
    }

    /** system property -> env var -> properties file -> default */
    private static String config(String property, String env, String fallback) {
        String v = System.getProperty("atm." + property);
        if (isSet(v)) {
            return v.trim();
        }
        v = System.getenv(env);
        if (isSet(v)) {
            return v.trim();
        }
        v = FILE_PROPS.getProperty(property);
        if (isSet(v)) {
            return v.trim();
        }
        v = FILE_PROPS.getProperty("atm." + property);
        return isSet(v) ? v.trim() : fallback;
    }

    private static boolean isSet(String v) {
        return v != null && !v.trim().isEmpty();
    }

    private static Properties loadPropertiesFile() {
        Properties p = new Properties();
        File[] candidates = {
            new File(System.getProperty("atm.db.config", "")),
            new File("atm-db.properties"),
            new File(System.getProperty("user.dir", "."), "atm-db.properties"),
            new File(System.getProperty("user.home", "."), "atm-db.properties")
        };
        for (File f : candidates) {
            if (f.getPath().isEmpty() || !f.isFile()) {
                continue;
            }
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
                return p;
            } catch (Exception ignored) {
            }
        }
        return p;
    }

    // --------------------------------------------------------------- self test

    /** Headless check: {@code java -cp atm-system.jar atm.system.Conn} */
    public static void main(String[] args) {
        System.out.println("ATM database check");
        System.out.println("  url  : " + fullUrl(config("db.name", "ATM_DB_NAME", DEFAULT_NAME)));
        try {
            Conn test = new Conn();
            if (test.c == null) {
                System.out.println("  RESULT: FAILED (see stack trace above)");
                System.exit(1);
            }
            ResultSet rs = test.s.executeQuery("SELECT COUNT(*) FROM login");
            rs.next();
            System.out.println("  RESULT: OK, login table has " + rs.getInt(1) + " row(s)");
        } catch (Exception e) {
            System.out.println("  RESULT: FAILED - " + e.getMessage());
            System.exit(1);
        }
    }
}
