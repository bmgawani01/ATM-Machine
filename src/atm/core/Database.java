package atm.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The application's connection pool, retry policy and health check.
 *
 * <p>One pool per JVM, created lazily. Money movement always runs inside
 * {@link #tx(TxWork)} so it commits as a unit or rolls back completely, and transient
 * deadlocks are retried instead of surfacing to the customer.
 */
public final class Database {

    /** Unit of work that runs inside one JDBC transaction. */
    public interface TxWork<T> {
        T run(Connection c) throws SQLException;
    }

    private static final int MAX_RETRIES = 3;
    private static volatile Database instance;

    private final HikariDataSource pool;
    private final String url;

    private Database() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL JDBC driver missing from the classpath", e);
        }
        this.url = Config.jdbcUrl();
        ensureDatabaseExists();
        this.pool = new HikariDataSource(config());
        Log.info("Connection pool ready (" + pool.getMaximumPoolSize() + " connections) -> "
            + Config.databaseName() + " at " + hostOf(url));
    }

    public static Database get() {
        Database local = instance;
        if (local == null) {
            synchronized (Database.class) {
                local = instance;
                if (local == null) {
                    local = new Database();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Close the pool; called on application shutdown. */
    public static synchronized void shutdown() {
        if (instance != null) {
            try {
                instance.pool.close();
            } catch (Exception e) {
                Log.warn("Pool close failed: " + e.getMessage());
            }
            instance = null;
        }
    }

    private HikariConfig config() {
        HikariConfig c = new HikariConfig();
        c.setPoolName("atm-pool");
        c.setJdbcUrl(url);
        c.setUsername(Config.get(Config.Setting.DB_USER));
        String password = Config.get(Config.Setting.DB_PASS);
        if (password == null) {
            // Better a clear instruction now than an access-denied error from the driver.
            Log.warn("No database password configured. Set " + Config.Setting.DB_PASS.env()
                + ", -Datm." + Config.Setting.DB_PASS.key() + "=..., or put db.pass in "
                + "atm-db.properties (copy atm-db.properties.example).");
        }
        c.setPassword(password);
        c.setDriverClassName("com.mysql.cj.jdbc.Driver");
        c.setMaximumPoolSize(Config.getInt(Config.Setting.DB_POOL_SIZE, 5));
        c.setMinimumIdle(1);
        c.setConnectionTimeout(20_000);
        c.setValidationTimeout(5_000);
        c.setIdleTimeout(300_000);
        c.setMaxLifetime(900_000);
        // do not fail the constructor: the GUI must be able to start and show a clear error
        c.setInitializationFailTimeout(-1);
        return c;
    }

    /** A fresh database on a free cloud instance has none of the tables; create them. */
    private void ensureDatabaseExists() {
        try (Connection c = DriverManager.getConnection(url, Config.get(Config.Setting.DB_USER),
                Config.get(Config.Setting.DB_PASS))) {
            Schema.ensure(c);
        } catch (SQLException first) {
            if (first.getErrorCode() != 1049) {
                Log.warn("Database not reachable yet: " + first.getMessage());
                return;
            }
            Log.info("Database " + Config.databaseName() + " does not exist yet, creating it");
            try (Connection c = DriverManager.getConnection(Config.jdbcUrlWithoutDatabase(),
                    Config.get(Config.Setting.DB_USER), Config.get(Config.Setting.DB_PASS));
                 Statement st = c.createStatement()) {
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + Config.databaseName() + "`");
            } catch (SQLException e) {
                throw new AtmException(AtmException.Reason.UNAVAILABLE,
                    "Cannot create the database: " + e.getMessage(), e);
            }
            // Reconnect on the full URL so the schema lands in the database just created. A
            // connection without one would run the DDL against no schema at all.
            try (Connection c = DriverManager.getConnection(url, Config.get(Config.Setting.DB_USER),
                    Config.get(Config.Setting.DB_PASS))) {
                Schema.ensure(c);
            } catch (SQLException e) {
                throw new AtmException(AtmException.Reason.UNAVAILABLE,
                    "Cannot reach the database: " + e.getMessage(), e);
            }
        }
    }

    /** A pooled connection. Callers that are not doing money movement may use this. */
    public Connection connection() throws SQLException {
        try {
            return pool.getConnection();
        } catch (SQLException e) {
            throw new AtmException(AtmException.Reason.UNAVAILABLE,
                "The bank database is not reachable right now", e);
        }
    }

    /**
     * Runs {@code work} in a single transaction. Commits on success, rolls back on any
     * exception, and retries transient deadlocks.
     */
    public <T> T tx(TxWork<T> work) {
        SQLException last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (Connection c = connection()) {
                boolean previousAutoCommit = c.getAutoCommit();
                c.setAutoCommit(false);
                try {
                    T result = work.run(c);
                    c.commit();
                    return result;
                } catch (Exception e) {
                    safeRollback(c);
                    if (e instanceof AtmException) {
                        throw (AtmException) e;
                    }
                    if (e instanceof SQLException) {
                        last = (SQLException) e;
                        if (attempt < MAX_RETRIES && isTransient(last)) {
                            Log.warn("Retrying transaction after " + last.getMessage());
                            sleep(attempt);
                            continue;
                        }
                        throw translate(last);
                    }
                    throw e;
                } finally {
                    try {
                        c.setAutoCommit(previousAutoCommit);
                    } catch (SQLException ignored) {
                        // connection is being returned to the pool anyway
                    }
                }
            } catch (SQLException e) {
                last = e;
                if (attempt >= MAX_RETRIES) {
                    throw translate(e);
                }
            }
        }
        throw translate(last);
    }

    /** Convenience for read-only work. */
    public <T> T read(SqlWork<T> work) {
        try (Connection c = connection()) {
            return work.run(c);
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /** Read-only unit of work. */
    public interface SqlWork<T> {
        T run(Connection c) throws SQLException;
    }

    private static boolean isTransient(SQLException e) {
        int code = e.getErrorCode();
        return code == 1213 || code == 1205 || code == 40001 || code == 1614;
    }

    private static void safeRollback(Connection c) {
        try {
            c.rollback();
        } catch (SQLException e) {
            Log.errorQuiet("Rollback failed", e);
        }
    }

    private static void sleep(int attempt) {
        try {
            Thread.sleep(60L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static AtmException translate(SQLException e) {
        if (e.getErrorCode() == 1062) {
            return new AtmException(AtmException.Reason.DUPLICATE, "That record already exists", e);
        }
        if (e.getErrorCode() == 1049 || e.getErrorCode() == 2003 || e.getErrorCode() == 1045
                || e.getErrorCode() == 1044 || e.getErrorCode() == 28) {
            return new AtmException(AtmException.Reason.UNAVAILABLE,
                "The bank database is not reachable right now", e);
        }
        return new AtmException(AtmException.Reason.DATABASE,
            "Database error: " + e.getMessage(), e);
    }

    /** Cheap round trip used by the health check and the admin dashboard. */
    public boolean healthy() {
        try (Connection c = connection();
             Statement st = c.createStatement()) {
            st.execute("SELECT 1");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public int poolActive() {
        return pool.getHikariPoolMXBean() == null ? 0 : pool.getHikariPoolMXBean().getActiveConnections();
    }

    public int poolIdle() {
        return pool.getHikariPoolMXBean() == null ? 0 : pool.getHikariPoolMXBean().getIdleConnections();
    }

    public String jdbcUrl() {
        return url;
    }

    private static String hostOf(String url) {
        int q = url.indexOf('?');
        String base = q < 0 ? url : url.substring(0, q);
        int slash = base.lastIndexOf('/');
        String host = slash < 0 ? base : base.substring(0, slash);
        return host.replace("jdbc:mysql://", "");
    }
}
