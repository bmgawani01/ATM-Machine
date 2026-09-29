package atm.core.dao;

import atm.core.Config;
import atm.core.Money;
import atm.core.Rows;
import atm.core.model.Limits;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Per-account limits, with the app-wide defaults as the fallback. */
public class LimitDao {

    public Limits find(Connection c, String accountNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT per_txn, daily_withdrawal, daily_transfers, daily_bill_payments, "
                    + "require_otp_above FROM account_limits WHERE account_no = ?")) {
            ps.setString(1, accountNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return defaults();
                }
                BigDecimal perTxn = Rows.moneyOrNull(rs, "per_txn");
                BigDecimal dailyWithdrawal = Rows.moneyOrNull(rs, "daily_withdrawal");
                Integer transfers = rs.getObject("daily_transfers") == null
                    ? null : rs.getInt("daily_transfers");
                Integer bills = rs.getObject("daily_bill_payments") == null
                    ? null : rs.getInt("daily_bill_payments");
                BigDecimal otpAbove = Rows.moneyOrNull(rs, "require_otp_above");
                return new Limits(
                    perTxn != null ? perTxn : money(Config.Setting.LIMIT_PER_TXN, "10000"),
                    dailyWithdrawal != null ? dailyWithdrawal
                        : money(Config.Setting.LIMIT_DAILY_WITHDRAWAL, "40000"),
                    transfers != null ? transfers
                        : Config.getInt(Config.Setting.LIMIT_DAILY_TRANSFERS, 5),
                    bills != null ? bills : 10,
                    otpAbove);
            }
        }
    }

    private Limits defaults() {
        return new Limits(
            money(Config.Setting.LIMIT_PER_TXN, "10000"),
            money(Config.Setting.LIMIT_DAILY_WITHDRAWAL, "40000"),
            Config.getInt(Config.Setting.LIMIT_DAILY_TRANSFERS, 5),
            10,
            BigDecimal.valueOf(5000));
    }

    public void upsert(Connection c, String accountNo, Limits limits) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO account_limits (account_no, per_txn, daily_withdrawal, "
                    + "daily_transfers, daily_bill_payments, require_otp_above, updated_at) "
                    + "VALUES (?,?,?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE per_txn = VALUES(per_txn), "
                    + "daily_withdrawal = VALUES(daily_withdrawal), "
                    + "daily_transfers = VALUES(daily_transfers), "
                    + "daily_bill_payments = VALUES(daily_bill_payments), "
                    + "require_otp_above = VALUES(require_otp_above), updated_at = NOW()")) {
            ps.setString(1, accountNo);
            ps.setBigDecimal(2, Money.scale(limits.perTxn()));
            ps.setBigDecimal(3, Money.scale(limits.dailyWithdrawal()));
            if (limits.dailyTransfers() == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, limits.dailyTransfers());
            }
            if (limits.dailyBillPayments() == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, limits.dailyBillPayments());
            }
            if (limits.requireOtpAbove() == null) {
                ps.setNull(6, java.sql.Types.DECIMAL);
            } else {
                ps.setBigDecimal(6, Money.scale(limits.requireOtpAbove()));
            }
            ps.executeUpdate();
        }
    }

    /** How much has already been withdrawn today, for the daily limit check. */
    public BigDecimal withdrawnToday(Connection c, String accountNo) throws SQLException {
        return sumToday(c, accountNo, "WITHDRAWAL", "FAST_CASH");
    }

    public long transfersToday(Connection c, String accountNo) throws SQLException {
        return countToday(c, accountNo, "TRANSFER_OUT");
    }

    public long billsToday(Connection c, String accountNo) throws SQLException {
        return countToday(c, accountNo, "BILL_PAYMENT");
    }

    private BigDecimal sumToday(Connection c, String accountNo, String... types) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE account_no = ? "
                + "AND created_at >= ? AND type IN (");
        for (int i = 0; i < types.length; i++) {
            sql.append(i == 0 ? "?" : ",?");
        }
        sql.append(")");
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setString(1, accountNo);
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDate.now().atStartOfDay()));
            for (int i = 0; i < types.length; i++) {
                ps.setString(3 + i, types[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Money.scale(rs.getBigDecimal(1)) : Money.ZERO;
            }
        }
    }

    private long countToday(Connection c, String accountNo, String type) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM transactions WHERE account_no = ? AND created_at >= ? "
                    + "AND type = ?")) {
            ps.setString(1, accountNo);
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDate.now().atStartOfDay()));
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static BigDecimal money(Config.Setting s, String fallback) {
        try {
            return new BigDecimal(Config.get(s, fallback));
        } catch (NumberFormatException e) {
            return new BigDecimal(fallback);
        }
    }
}
