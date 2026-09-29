package atm.core.dao;

import atm.core.Money;
import atm.core.Rows;
import atm.core.model.TransactionType;
import atm.core.model.Txn;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** The ledger: deposits, withdrawals, transfers, bill payments and airtime purchases. */
public class TxnDao {

    private static final String COLUMNS =
        "id, account_no, type, amount, balance_after, channel, note, reference, "
            + "idempotency_key, status, created_at";

    public Txn insert(Connection c, String accountNo, TransactionType type, BigDecimal amount,
            BigDecimal balanceAfter, String note, String reference, String idempotencyKey,
            String channel) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO transactions (account_no, type, amount, balance_after, channel, note, "
                    + "reference, idempotency_key, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS')",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, accountNo);
            ps.setString(2, type.name());
            ps.setBigDecimal(3, Money.scale(amount));
            ps.setBigDecimal(4, Money.scale(balanceAfter));
            ps.setString(5, channel == null ? "ATM" : channel);
            ps.setString(6, note);
            ps.setString(7, reference);
            ps.setString(8, idempotencyKey);
            ps.executeUpdate();
            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                id = keys.next() ? keys.getLong(1) : 0L;
            }
            return new Txn(id, accountNo, type, Money.scale(amount), Money.scale(balanceAfter),
                channel == null ? "ATM" : channel, note, reference, idempotencyKey, "SUCCESS",
                LocalDateTime.now());
        }
    }

    public Txn findById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM transactions WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * The duplicate-transaction guard: if the same idempotency key is already in the ledger
     * the money already moved, and the caller must not move it again.
     */
    public Txn findByIdempotencyKey(Connection c, String key) throws SQLException {
        if (key == null || key.isBlank()) {
            return null;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM transactions WHERE idempotency_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<Txn> listByAccount(Connection c, String accountNo, LocalDate from, LocalDate to,
            TransactionType type, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM transactions WHERE account_no = ?");
        List<Object> args = new ArrayList<>();
        args.add(accountNo);
        if (from != null) {
            sql.append(" AND created_at >= ?");
            args.add(java.sql.Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            sql.append(" AND created_at < ?");
            args.add(java.sql.Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        if (type != null) {
            sql.append(" AND type = ?");
            args.add(type.name());
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ").append(Math.max(1, Math.min(limit, 500)));
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<Txn> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        }
    }

    /** Newest transactions across every account, for the admin monitoring screen. */
    public List<Txn> recent(Connection c, int limit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM transactions ORDER BY created_at DESC, id DESC LIMIT "
                    + Math.max(1, Math.min(limit, 500)))) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Txn> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        }
    }

    public BigDecimal sumSince(Connection c, String accountNo, LocalDateTime since,
            TransactionType type) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM transactions "
            + "WHERE created_at >= ? AND status = 'SUCCESS'"
            + (accountNo == null ? "" : " AND account_no = ?")
            + (type == null ? "" : " AND type = ?");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setTimestamp(i++, java.sql.Timestamp.valueOf(since));
            if (accountNo != null) {
                ps.setString(i++, accountNo);
            }
            if (type != null) {
                ps.setString(i++, type.name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Money.scale(rs.getBigDecimal(1)) : Money.ZERO;
            }
        }
    }

    public long countSince(Connection c, LocalDateTime since, TransactionType type) throws SQLException {
        String sql = "SELECT COUNT(*) FROM transactions WHERE created_at >= ?"
            + (type == null ? "" : " AND type = ?");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(since));
            if (type != null) {
                ps.setString(2, type.name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    public long countToday(Connection c) throws SQLException {
        return countSince(c, LocalDate.now().atStartOfDay(), null);
    }

    public BigDecimal sumByTypeToday(Connection c, TransactionType type) throws SQLException {
        return sumSince(c, null, LocalDate.now().atStartOfDay(), type);
    }

    private static void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            Object a = args.get(i);
            int n = i + 1;
            if (a instanceof String) {
                ps.setString(n, (String) a);
            } else if (a instanceof LocalDate) {
                ps.setTimestamp(n, java.sql.Timestamp.valueOf(((LocalDate) a).atStartOfDay()));
            } else {
                ps.setObject(n, a);
            }
        }
    }

    private static Txn map(ResultSet rs) throws SQLException {
        return new Txn(
            rs.getLong("id"),
            Rows.str(rs, "account_no"),
            TransactionType.of(Rows.str(rs, "type")),
            Rows.money(rs, "amount"),
            Rows.money(rs, "balance_after"),
            Rows.str(rs, "channel"),
            Rows.str(rs, "note"),
            Rows.str(rs, "reference"),
            Rows.str(rs, "idempotency_key"),
            Rows.str(rs, "status"),
            Rows.time(rs, "created_at"));
    }
}
