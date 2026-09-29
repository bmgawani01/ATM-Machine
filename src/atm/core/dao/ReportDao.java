package atm.core.dao;

import atm.core.Money;
import atm.core.Rows;
import atm.core.model.CardStatus;
import atm.core.model.DashboardStats;
import atm.core.model.TransactionType;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Aggregations for the admin dashboard and the printable reports. */
public class ReportDao {

    public DashboardStats stats(Connection c) throws SQLException {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long transactionsToday = new TxnDao().countSince(c, startOfDay, null);
        BigDecimal volumeToday = new TxnDao().sumSince(c, null, startOfDay, null);
        BigDecimal deposits = new TxnDao().sumByTypeToday(c, TransactionType.DEPOSIT);
        BigDecimal withdrawals = new TxnDao().sumByTypeToday(c, TransactionType.WITHDRAWAL)
            .add(new TxnDao().sumByTypeToday(c, TransactionType.FAST_CASH));
        long failed = new AuditDao().failedSince(c, startOfDay, null);
        return new DashboardStats(
            new CustomerDao().count(c),
            new AccountDao().count(c),
            new CardDao().count(c, CardStatus.ACTIVE),
            new CardDao().count(c, CardStatus.BLOCKED),
            transactionsToday,
            volumeToday,
            deposits,
            withdrawals,
            failed,
            new AtmCashDao().total(c));
    }

    /** One row per day: date, deposits, withdrawals, transfers, bill payments, count. */
    public List<String[]> dailyReport(Connection c, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT DATE(created_at) AS d, "
            + "COALESCE(SUM(IF(type = 'DEPOSIT', amount, 0)), 0) AS deposits, "
            + "COALESCE(SUM(IF(type IN ('WITHDRAWAL','FAST_CASH'), amount, 0)), 0) AS withdrawals, "
            + "COALESCE(SUM(IF(type IN ('TRANSFER_OUT','TRANSFER_IN'), amount, 0)), 0) AS transfers, "
            + "COALESCE(SUM(IF(type = 'BILL_PAYMENT', amount, 0)), 0) AS bills, "
            + "COUNT(*) AS n FROM transactions WHERE created_at >= ? AND created_at < ? "
            + "GROUP BY DATE(created_at) ORDER BY d";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                List<String[]> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new String[] {
                        String.valueOf(rs.getDate("d")),
                        Money.plain(rs.getBigDecimal("deposits")),
                        Money.plain(rs.getBigDecimal("withdrawals")),
                        Money.plain(rs.getBigDecimal("transfers")),
                        Money.plain(rs.getBigDecimal("bills")),
                        String.valueOf(rs.getLong("n"))
                    });
                }
                return out;
            }
        }
    }

    /** Totals per transaction type, for the transaction report. */
    public List<String[]> typeReport(Connection c, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT type, COUNT(*) AS n, COALESCE(SUM(amount), 0) AS total "
            + "FROM transactions WHERE created_at >= ? AND created_at < ? "
            + "GROUP BY type ORDER BY total DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                List<String[]> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new String[] {
                        TransactionType.of(Rows.str(rs, "type")).label(),
                        String.valueOf(rs.getLong("n")),
                        Money.plain(rs.getBigDecimal("total"))
                    });
                }
                return out;
            }
        }
    }

    /** Busiest customers by transaction value, joined with the name. */
    public List<String[]> topCustomers(Connection c, LocalDate from, LocalDate to, int limit)
            throws SQLException {
        String sql = "SELECT cu.name, a.cardno, COUNT(t.id) AS n, COALESCE(SUM(t.amount), 0) AS total "
            + "FROM transactions t "
            + "JOIN accounts a ON a.account_no = t.account_no "
            + "JOIN customers cu ON cu.cardno = a.cardno "
            + "WHERE t.created_at >= ? AND t.created_at < ? "
            + "GROUP BY t.account_no, a.cardno, cu.name ORDER BY total DESC LIMIT "
            + Math.max(1, Math.min(limit, 50));
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                List<String[]> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new String[] {
                        Rows.str(rs, "name"), Rows.str(rs, "cardno"),
                        String.valueOf(rs.getLong("n")), Money.plain(rs.getBigDecimal("total"))
                    });
                }
                return out;
            }
        }
    }
}
