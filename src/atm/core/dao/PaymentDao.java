package atm.core.dao;

import atm.core.Money;
import atm.core.Rows;
import atm.core.model.Biller;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Utility billers and the record of bill / airtime purchases. */
public class PaymentDao {

    public List<Biller> billers(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, name, category, customer_ref_label FROM billers "
                    + "WHERE active = 1 ORDER BY category, name");
             ResultSet rs = ps.executeQuery()) {
            List<Biller> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new Biller(
                    rs.getLong("id"),
                    Rows.str(rs, "name"),
                    Rows.str(rs, "category"),
                    Rows.str(rs, "customer_ref_label")));
            }
            return out;
        }
    }

    public Biller biller(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, name, category, customer_ref_label FROM billers WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Biller(rs.getLong("id"), Rows.str(rs, "name"),
                    Rows.str(rs, "category"), Rows.str(rs, "customer_ref_label")) : null;
            }
        }
    }

    public void recordBillPayment(Connection c, String accountNo, long billerId, String customerRef,
            BigDecimal amount, long txnId, String reference) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bill_payments (account_no, biller_id, customer_ref, amount, txn_id, "
                    + "reference) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, accountNo);
            ps.setLong(2, billerId);
            ps.setString(3, customerRef);
            ps.setBigDecimal(4, Money.scale(amount));
            ps.setLong(5, txnId);
            ps.setString(6, reference);
            ps.executeUpdate();
        }
    }

    public void recordAirtime(Connection c, String accountNo, String network, String phone,
            BigDecimal amount, long txnId, String reference) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO airtime_purchases (account_no, network, phone, amount, txn_id, "
                    + "reference) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, accountNo);
            ps.setString(2, network);
            ps.setString(3, phone);
            ps.setBigDecimal(4, Money.scale(amount));
            ps.setLong(5, txnId);
            ps.setString(6, reference);
            ps.executeUpdate();
        }
    }

    public List<String[]> recentBillPayments(Connection c, int limit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT b.name, p.customer_ref, p.amount, p.reference, p.created_at "
                    + "FROM bill_payments p JOIN billers b ON b.id = p.biller_id "
                    + "ORDER BY p.id DESC LIMIT " + Math.max(1, Math.min(limit, 200)))) {
            return read(ps);
        }
    }

    public List<String[]> recentAirtime(Connection c, int limit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT network, phone, amount, reference, created_at FROM airtime_purchases "
                    + "ORDER BY id DESC LIMIT " + Math.max(1, Math.min(limit, 200)))) {
            return read(ps);
        }
    }

    private List<String[]> read(PreparedStatement ps) throws SQLException {
        List<String[]> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new String[] {
                    Rows.str(rs, "network"), Rows.str(rs, "phone"), Rows.str(rs, "amount"),
                    Rows.str(rs, "reference"), String.valueOf(Rows.time(rs, "created_at"))
                });
            }
        }
        return out;
    }
}
