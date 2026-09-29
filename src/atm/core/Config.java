package atm.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Single place where every setting is resolved, so nothing else in the app reads
 * System.getenv directly. Resolution order for every key (first match wins):
 *
 * <ol>
 *   <li>JVM system property, e.g. {@code -Datm.session.timeout=180}</li>
 *   <li>Environment variable, e.g. {@code ATM_SESSION_TIMEOUT}</li>
 *   <li>{@code atm-db.properties} / {@code atm.properties} in the working directory,
 *       next to the jar, or in the user home (keys may be written as {@code db.url} or
 *       {@code atm.db.url})</li>
 *   <li>The built-in default</li>
 * </ol>
 *
 * <p>Keys are declared in {@link Setting}. Anything prefixed {@code db.} comes from the
 * properties file next to the app; the free cloud instance details live there.
 */
public final class Config {

    public enum Setting {
        // database
        DB_URL("db.url", "ATM_DB_URL", null),
        DB_HOST("db.host", "ATM_DB_HOST", "localhost"),
        DB_PORT("db.port", "ATM_DB_PORT", "3306"),
        DB_NAME("db.name", "ATM_DB_NAME", "bankmanagementsystem_db"),
        DB_USER("db.user", "ATM_DB_USER", "root"),
        // No password is built in. A credential in source ends up in git history and in every
        // clone, so the password must come from the environment or atm-db.properties.
        DB_PASS("db.pass", "ATM_DB_PASS", null),
        DB_PARAMS("db.params", "ATM_DB_PARAMS",
                "sslMode=PREFERRED&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                    + "&connectTimeout=15000&socketTimeout=30000&useUnicode=true"
                    + "&characterEncoding=UTF-8"),
        DB_POOL_SIZE("db.pool.size", "ATM_DB_POOL_SIZE", "5"),
        // security policy
        MAX_PIN_ATTEMPTS("security.max.pin.attempts", "ATM_MAX_PIN_ATTEMPTS", "3"),
        LOCK_MINUTES("security.lock.minutes", "ATM_LOCK_MINUTES", "5"),
        SESSION_TIMEOUT_SECONDS("session.timeout.seconds", "ATM_SESSION_TIMEOUT_SECONDS", "120"),
        OTP_TTL_SECONDS("security.otp.ttl.seconds", "ATM_OTP_TTL_SECONDS", "120"),
        OTP_MAX_ATTEMPTS("security.otp.max.attempts", "ATM_OTP_MAX_ATTEMPTS", "3"),
        OTP_DELIVERY("security.otp.delivery", "ATM_OTP_DELIVERY", "CONSOLE"),
        // transaction limits (fallback when an account has no limit row)
        LIMIT_PER_TXN("limits.per.txn", "ATM_LIMIT_PER_TXN", "10000"),
        LIMIT_DAILY_WITHDRAWAL("limits.daily.withdrawal", "ATM_LIMIT_DAILY_WITHDRAWAL", "40000"),
        LIMIT_DAILY_TRANSFERS("limits.daily.transfers", "ATM_LIMIT_DAILY_TRANSFERS", "5"),
        // misc
        ATM_CURRENCY("atm.currency", "ATM_CURRENCY", "USD"),
        PROFILE("app.profile", "ATM_PROFILE", "desktop"),
        LEGACY_MIGRATION("app.legacy.migration", "ATM_LEGACY_MIGRATION", "true");

        private final String key;
        private final String env;
        private final String fallback;

        Setting(String key, String env, String fallback) {
            this.key = key;
            this.env = env;
            this.fallback = fallback;
        }

        public String key() {
            return key;
        }

        public String env() {
            return env;
        }

        public String fallback() {
            return fallback;
        }
    }

    private static final Properties FILE = loadFile();

    private Config() {
    }

    public static String get(Setting s) {
        return get(s, s.fallback());
    }

    public static String get(Setting s, String fallback) {
        String v = System.getProperty("atm." + s.key());
        if (set(v)) {
            return v.trim();
        }
        v = System.getenv(s.env());
        if (set(v)) {
            return v.trim();
        }
        v = FILE.getProperty(s.key());
        if (set(v)) {
            return v.trim();
        }
        v = FILE.getProperty("atm." + s.key());
        if (set(v)) {
            return v.trim();
        }
        return fallback;
    }

    public static int getInt(Setting s, int fallback) {
        try {
            return Integer.parseInt(get(s, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean getBool(Setting s, boolean fallback) {
        String v = get(s, String.valueOf(fallback));
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }

    public static boolean set(String v) {
        return v != null && !v.trim().isEmpty();
    }

    /** Full JDBC URL, either taken verbatim from ATM_DB_URL or assembled from the parts. */
    public static String jdbcUrl() {
        String explicit = get(Setting.DB_URL, null);
        if (explicit != null) {
            return explicit;
        }
        return "jdbc:mysql://" + get(Setting.DB_HOST) + ":" + get(Setting.DB_PORT) + "/"
            + get(Setting.DB_NAME) + "?" + get(Setting.DB_PARAMS);
    }

    /** The same URL without the database name, used to CREATE DATABASE on a fresh server. */
    public static String jdbcUrlWithoutDatabase() {
        String url = jdbcUrl();
        int q = url.indexOf('?');
        String base = q < 0 ? url : url.substring(0, q);
        String params = q < 0 ? "" : url.substring(q);
        int slash = base.lastIndexOf('/');
        return slash < 0 ? base : base.substring(0, slash) + "/" + params;
    }

    public static String databaseName() {
        String url = jdbcUrl();
        int q = url.indexOf('?');
        String base = q < 0 ? url : url.substring(0, q);
        int slash = base.lastIndexOf('/');
        return slash < 0 ? "" : base.substring(slash + 1);
    }

    private static Properties loadFile() {
        Properties p = new Properties();
        File[] candidates = {
            new File(System.getProperty("atm.config", "")),
            new File("atm-db.properties"),
            new File("atm.properties"),
            new File(System.getProperty("user.dir", "."), "atm-db.properties"),
            new File(System.getProperty("user.dir", "."), "atm.properties"),
            new File(System.getProperty("user.home", "."), "atm-db.properties")
        };
        for (File f : candidates) {
            if (f.getPath().isEmpty() || !f.isFile()) {
                continue;
            }
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
                return p;
            } catch (Exception e) {
                Log.warn("Could not read " + f + ": " + e.getMessage());
            }
        }
        return p;
    }
}
