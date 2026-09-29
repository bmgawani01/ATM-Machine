package atm.core;

import atm.core.dao.LegacyImporter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Schema owner. Creates everything the app needs on first connect, so pointing it at an
 * empty free cloud database is enough - there is no separate migration step to forget.
 *
 * <p>Portable across MySQL 8 and TiDB Cloud: no engine clauses, no stored procedures, no
 * foreign keys (TiDB does not enforce them), no {@code ON UPDATE} magic.
 *
 * <p>The original four tables ({@code login}, {@code bank}, {@code signup*}) are kept and
 * maintained so nothing that used them before breaks, and are used as the source for the
 * one-time import in {@code db/migrate-legacy.sql}.
 */
public final class Schema {

    private static final Logger LOG = Logger.getLogger("atm.schema");

    private static final String[] DDL = {
        // ------------------------------------------------------------- customers
        "CREATE TABLE IF NOT EXISTS customers ("
            + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "cardno VARCHAR(19) NOT NULL, "
            + "name VARCHAR(60) NOT NULL, fname VARCHAR(60), dob VARCHAR(30), gender VARCHAR(10), "
            + "email VARCHAR(80), marital VARCHAR(12), address VARCHAR(150), city VARCHAR(40), "
            + "religion VARCHAR(20), category VARCHAR(20), income VARCHAR(30), education VARCHAR(20), "
            + "occupation VARCHAR(20), pan VARCHAR(30), aadhar VARCHAR(30), phone VARCHAR(20), "
            + "services VARCHAR(200), acctype VARCHAR(30), status VARCHAR(16) DEFAULT 'ACTIVE', "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "UNIQUE KEY uk_customers_cardno (cardno))",

        // -------------------------------------------------------------- accounts
        "CREATE TABLE IF NOT EXISTS accounts ("
            + "account_no VARCHAR(19) NOT NULL PRIMARY KEY, "
            + "cardno VARCHAR(19) NOT NULL, "
            + "type VARCHAR(20) NOT NULL DEFAULT 'SAVING', "
            + "balance DECIMAL(15,2) NOT NULL DEFAULT 0.00, "
            + "status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE', "
            + "currency CHAR(3) DEFAULT 'USD', "
            + "opened_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "KEY ix_accounts_cardno (cardno))",

        // ----------------------------------------------------------------- cards
        "CREATE TABLE IF NOT EXISTS cards ("
            + "cardno VARCHAR(19) NOT NULL PRIMARY KEY, "
            + "account_no VARCHAR(19) NOT NULL, "
            + "pin_hash VARCHAR(160) NOT NULL, "
            + "pin_salt VARCHAR(64) NOT NULL, "
            + "status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE', "
            + "failed_attempts INT NOT NULL DEFAULT 0, "
            + "locked_until TIMESTAMP NULL, "
            + "expires_on DATE NULL, "
            + "issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "last_used_at TIMESTAMP NULL, "
            + "KEY ix_cards_account (account_no))",

        // ---------------------------------------------------------- transactions
        "CREATE TABLE IF NOT EXISTS transactions ("
            + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "account_no VARCHAR(19) NOT NULL, "
            + "type VARCHAR(24) NOT NULL, "
            + "amount DECIMAL(15,2) NOT NULL, "
            + "balance_after DECIMAL(15,2) NOT NULL, "
            + "channel VARCHAR(12) NOT NULL DEFAULT 'ATM', "
            + "note VARCHAR(150), "
            + "reference VARCHAR(40), "
            + "idempotency_key VARCHAR(64) UNIQUE, "
            + "status VARCHAR(12) NOT NULL DEFAULT 'SUCCESS', "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "KEY ix_txn_account_date (account_no, created_at), "
            + "KEY ix_txn_type_date (type, created_at))",

        "CREATE TABLE IF NOT EXISTS transfers ("
            + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "from_account VARCHAR(19) NOT NULL, "
            + "to_account VARCHAR(19) NOT NULL, "
            + "amount DECIMAL(15,2) NOT NULL, "
            + "from_ref VARCHAR(40), to_ref VARCHAR(40), "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS billers ("
            + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "name VARCHAR(60) NOT NULL, category VARCHAR(40) NOT NULL, "
            + "customer_ref_label VARCHAR(40), active TINYINT NOT NULL DEFAULT 1)",

        "CREATE TABLE IF NOT EXISTS bill_payments ("
            + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "account_no VARCHAR(19) NOT NULL, biller_id BIGINT NOT NULL, "
            + "customer_ref VARCHAR(60), amount DECIMAL(15,2) NOT NULL, "
            + "txn_id BIGINT, reference VARCHAR(40), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS airtime_purchases ("
            + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "account_no VARCHAR(19) NOT NULL, network VARCHAR(30) NOT NULL, "
            + "phone VARCHAR(20) NOT NULL, amount DECIMAL(15,2) NOT NULL, "
            + "txn_id BIGINT, reference VARCHAR(40), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",

        // ------------------------------------------------------------- security
        "CREATE TABLE IF NOT EXISTS otp_challenges ("
            + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "cardno VARCHAR(19) NOT NULL, purpose VARCHAR(32) NOT NULL, "
            + "code_hash VARCHAR(160) NOT NULL, code_salt VARCHAR(64) NOT NULL, "
            + "expires_at TIMESTAMP NOT NULL, consumed_at TIMESTAMP NULL, "
            + "attempts INT NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "KEY ix_otp_card (cardno, purpose))",

        "CREATE TABLE IF NOT EXISTS sessions ("
            + "id VARCHAR(64) NOT NULL PRIMARY KEY, "
            + "cardno VARCHAR(19), account_no VARCHAR(19), "
            + "channel VARCHAR(12) DEFAULT 'DESKTOP', "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "expires_at TIMESTAMP NOT NULL, "
            + "revoked TINYINT NOT NULL DEFAULT 0, "
            + "revoked_reason VARCHAR(40))",

        "CREATE TABLE IF NOT EXISTS audit_log ("
            + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "actor VARCHAR(32), cardno VARCHAR(19), action VARCHAR(40) NOT NULL, "
            + "details VARCHAR(255), ip VARCHAR(45), "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "KEY ix_audit_action (action, created_at), "
            + "KEY ix_audit_card (cardno, created_at))",

        "CREATE TABLE IF NOT EXISTS failed_logins ("
            + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
            + "cardno VARCHAR(19), reason VARCHAR(40), ip VARCHAR(45), "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "KEY ix_failed_card (cardno, created_at))",

        "CREATE TABLE IF NOT EXISTS account_limits ("
            + "account_no VARCHAR(19) NOT NULL PRIMARY KEY, "
            + "per_txn DECIMAL(15,2), daily_withdrawal DECIMAL(15,2), "
            + "daily_transfers INT, daily_bill_payments INT, "
            + "require_otp_above DECIMAL(15,2), "
            + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS admin_users ("
            + "username VARCHAR(32) NOT NULL PRIMARY KEY, "
            + "full_name VARCHAR(60), role VARCHAR(20) NOT NULL DEFAULT 'TELLER', "
            + "pin_hash VARCHAR(160) NOT NULL, pin_salt VARCHAR(64) NOT NULL, "
            + "status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE', "
            + "last_login TIMESTAMP NULL, failed_attempts INT NOT NULL DEFAULT 0, "
            + "locked_until TIMESTAMP NULL)",

        // --------------------------------------------------------- machine cash float
        "CREATE TABLE IF NOT EXISTS atm_cash ("
            + "denom INT NOT NULL PRIMARY KEY, "
            + "notes INT NOT NULL DEFAULT 0, "
            + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",

        // ------------------------------------------------- legacy (pre-rewrite) tables
        "CREATE TABLE IF NOT EXISTS login ("
            + "cardno VARCHAR(19) NOT NULL PRIMARY KEY, pin VARCHAR(32) NOT NULL)",
        "CREATE TABLE IF NOT EXISTS bank ("
            + "pin VARCHAR(16), date VARCHAR(80), mode VARCHAR(20), amount VARCHAR(40))",
        "CREATE TABLE IF NOT EXISTS signup ("
            + "formno VARCHAR(16) NOT NULL PRIMARY KEY, name VARCHAR(60), fname VARCHAR(60), "
            + "dob VARCHAR(40), gender VARCHAR(16), email VARCHAR(80), marital VARCHAR(20), "
            + "address VARCHAR(150), city VARCHAR(40))",
        "CREATE TABLE IF NOT EXISTS signup2 ("
            + "formno VARCHAR(16) NOT NULL PRIMARY KEY, religion VARCHAR(20), category VARCHAR(20), "
            + "income VARCHAR(30), education VARCHAR(20), occupation VARCHAR(20), pan VARCHAR(30), "
            + "aadhar VARCHAR(30), scitizen VARCHAR(10), eaccount VARCHAR(10))",
        "CREATE TABLE IF NOT EXISTS signupthree ("
            + "formno VARCHAR(16) NOT NULL PRIMARY KEY, acctype VARCHAR(30), "
            + "services VARCHAR(200), e_account VARCHAR(10), pin VARCHAR(16))"
    };

    private Schema() {
    }

    /**
     * Creates missing tables and reference data. Safe to call on every start.
     *
     * <p>Every statement runs even if an earlier one failed, so one broken piece of DDL cannot
     * leave the rest of the schema uncreated. If anything did fail, the first error is reported
     * rather than swallowed: carrying on with a half-built schema only moves the failure to
     * whichever screen happens to touch the missing table first.
     */
    public static void ensure(Connection c) {
        SQLException firstFailure = null;
        for (String ddl : DDL) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate(ddl);
            } catch (SQLException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        try (Statement st = c.createStatement()) {
            alignLegacyBankColumn(st);
            seedReferenceData(c, st);
        } catch (SQLException e) {
            if (firstFailure == null) {
                firstFailure = e;
            }
        }
        if (firstFailure != null) {
            LOG.log(Level.WARNING, "Schema bootstrap failed: " + firstFailure.getMessage());
            throw new AtmException(AtmException.Reason.UNAVAILABLE,
                "The database schema is incomplete: " + firstFailure.getMessage(), firstFailure);
        }
        LOG.log(Level.INFO, "Schema ready");
    }

    /** Old installs called the column {@code type}; every screen reads {@code mode}. */
    private static void alignLegacyBankColumn(Statement st) throws SQLException {
        boolean hasMode = columnExists(st, "bank", "mode");
        boolean hasType = columnExists(st, "bank", "type");
        if (hasType && !hasMode) {
            st.executeUpdate("ALTER TABLE bank CHANGE COLUMN type mode VARCHAR(20)");
            LOG.log(Level.INFO, "bank.type renamed to bank.mode");
        }
    }

    private static boolean columnExists(Statement st, String table, String column)
            throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' "
                    + "AND column_name = '" + column + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /** Biller list, ATM cash float, admin user and the demo customer. */
    private static void seedReferenceData(Connection c, Statement st) throws SQLException {
        insertIfMissing(st, "SELECT COUNT(*) FROM billers", "INSERT INTO billers "
            + "(name, category, customer_ref_label) VALUES "
            + "('ZESA Electricity', 'Utilities', 'Meter Number'),"
            + "('Zimtel Broadband', 'Utilities', 'Account Number'),"
            + "('Liquid Telecom', 'Telecom', 'Phone Number'),"
            + "('NetOne Data Bundle', 'Telecom', 'Phone Number'),"
            + "('City Council Rates', 'Utilities', 'Account Number'),"
            + "('StarLife Insurance', 'Insurance', 'Policy Number')");

        insertIfMissing(st, "SELECT COUNT(*) FROM atm_cash", "INSERT INTO atm_cash (denom, notes) "
            + "VALUES (20,2000),(50,1000),(100,1000),(200,500),(500,200),(1000,100),(2000,50),(5000,20)");

        if (count(st, "SELECT COUNT(*) FROM admin_users") == 0) {
            insertAdmin(st, "admin", "Branch Manager", "ADMIN", "1234");
        }

        if (Config.getBool(Config.Setting.LEGACY_MIGRATION, true)) {
            LegacyImporter.importOnce(c);
        }
    }

    private static void insertIfMissing(Statement st, String check, String insert)
            throws SQLException {
        if (insert == null || count(st, check) > 0) {
            return;
        }
        st.executeUpdate(insert);
    }

    private static int count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    static void insertAdmin(Statement st, String username, String fullName, String role, String pin)
            throws SQLException {
        String salt = Passwords.newSalt();
        st.executeUpdate("INSERT INTO admin_users (username, full_name, role, pin_hash, pin_salt) "
            + "VALUES ('" + username + "', '" + fullName + "', '" + role + "', '"
            + Passwords.hash(pin, salt) + "', '" + salt + "')");
    }
}
