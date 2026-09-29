package atm.core.dao;

import atm.core.AtmException;
import atm.core.Money;
import atm.core.Rows;
import atm.core.model.Account;
import atm.core.model.AccountType;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Accounts and balances.
 *
 * <p>All methods take the caller's {@link Connection} so a balance change and the
 * transaction row that explains it can share one JDBC transaction.
 */
public class AccountDao {

    private static final String COLUMNS =
        "account_no, cardno, type, balance, status, currency, opened_at";

    public Account findByCardNo(Connection c, String cardNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM accounts WHERE cardno = ? ORDER BY opened_at LIMIT 1")) {
            ps.setString(1, cardNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public Account findByNo(Connection c, String accountNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM accounts WHERE account_no = ?")) {
            ps.setString(1, accountNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Row lock for update; the caller must already be inside a transaction. */
    public Account lock(Connection c, String accountNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM accounts WHERE account_no = ? FOR UPDATE")) {
            ps.setString(1, accountNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AtmException(AtmException.Reason.NOT_FOUND, "Account not found");
                }
                return map(rs);
            }
        }
    }

    public void insert(Connection c, String accountNo, String cardNo, AccountType type,
            BigDecimal openingBalance) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO accounts (account_no, cardno, type, balance, status, currency) "
                    + "VALUES (?, ?, ?, ?, 'ACTIVE', ?)")) {
            ps.setString(1, accountNo);
            ps.setString(2, cardNo);
            ps.setString(3, type.code());
            ps.setBigDecimal(4, Money.scale(openingBalance));
            ps.setString(5, atm.core.Config.get(atm.core.Config.Setting.ATM_CURRENCY));
            ps.executeUpdate();
        }
    }

    /**
     * Guarded debit: the balance is only reduced when there is enough money, and the check
     * and the update are one statement, so two concurrent withdrawals cannot both succeed.
     *
     * @return the new balance
     */
    public BigDecimal debit(Connection c, String accountNo, BigDecimal amount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE accounts SET balance = balance - ? "
                    + "WHERE account_no = ? AND status = 'ACTIVE' AND balance >= ?")) {
            ps.setBigDecimal(1, Money.scale(amount));
            ps.setString(2, accountNo);
            ps.setBigDecimal(3, Money.scale(amount));
            if (ps.executeUpdate() != 1) {
                Account a = findByNo(c, accountNo);
                if (a == null) {
                    throw new AtmException(AtmException.Reason.NOT_FOUND, "Account not found");
                }
                if (!a.isActive()) {
                    throw new AtmException(AtmException.Reason.BLOCKED,
                        "Account " + a.accountNo() + " is " + a.status().toLowerCase());
                }
                throw new AtmException(AtmException.Reason.INSUFFICIENT_FUNDS,
                    "Insufficient balance. Available: " + Money.usd(a.balance()));
            }
        }
        return balance(c, accountNo);
    }

    public BigDecimal credit(Connection c, String accountNo, BigDecimal amount)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE accounts SET balance = balance + ? "
                    + "WHERE account_no = ? AND status <> 'CLOSED'")) {
            ps.setBigDecimal(1, Money.scale(amount));
            ps.setString(2, accountNo);
            if (ps.executeUpdate() != 1) {
                throw new AtmException(AtmException.Reason.NOT_FOUND, "Account not found");
            }
        }
        return balance(c, accountNo);
    }

    public BigDecimal balance(Connection c, String accountNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT balance FROM accounts WHERE account_no = ?")) {
            ps.setString(1, accountNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AtmException(AtmException.Reason.NOT_FOUND, "Account not found");
                }
                return Rows.money(rs, "balance");
            }
        }
    }

    public void setStatus(Connection c, String accountNo, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE accounts SET status = ? WHERE account_no = ?")) {
            ps.setString(1, status);
            ps.setString(2, accountNo);
            ps.executeUpdate();
        }
    }

    public void setType(Connection c, String accountNo, AccountType type) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE accounts SET type = ? WHERE account_no = ?")) {
            ps.setString(1, type.code());
            ps.setString(2, accountNo);
            ps.executeUpdate();
        }
    }

    public List<Account> list(Connection c, String search, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM accounts WHERE 1 = 1";
        if (search != null && !search.isBlank()) {
            sql += " AND (account_no LIKE ? OR cardno LIKE ?)";
        }
        sql += " ORDER BY opened_at DESC LIMIT " + Math.max(1, Math.min(limit, 500));
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim() + "%";
                ps.setString(1, like);
                ps.setString(2, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Account> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        }
    }

    public long count(Connection c) throws SQLException {
        return scalar(c, "SELECT COUNT(*) FROM accounts");
    }

    public BigDecimal totalBalance(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(balance), 0) FROM accounts WHERE status = 'ACTIVE'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Money.scale(rs.getBigDecimal(1)) : Money.ZERO;
        }
    }

    static long scalar(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static Account map(ResultSet rs) throws SQLException {
        return new Account(
            Rows.str(rs, "account_no"),
            Rows.str(rs, "cardno"),
            AccountType.fromLabel(Rows.str(rs, "type")),
            Rows.money(rs, "balance"),
            Rows.str(rs, "status"),
            Rows.str(rs, "currency"),
            Rows.time(rs, "opened_at"));
    }
}
