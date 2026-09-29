package atm.core.dao;

import atm.core.Rows;
import atm.core.model.Customer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Customer profiles. */
public class CustomerDao {

    private static final String COLUMNS =
        "id, cardno, name, fname, dob, gender, email, marital, address, city, religion, category, "
            + "income, education, occupation, pan, aadhar, phone, services, acctype, status, created_at";

    public Customer findByCardNo(Connection c, String cardNo) throws SQLException {
        return findBy(c, "cardno", cardNo);
    }

    public Customer findById(Connection c, long id) throws SQLException {
        return findBy(c, "id", String.valueOf(id));
    }

    private Customer findBy(Connection c, String column, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM customers WHERE " + column + " = ? LIMIT 1")) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public long insert(Connection c, String cardNo, String name, String fname, String dob,
            String gender, String email, String marital, String address, String city,
            String religion, String category, String income, String education, String occupation,
            String pan, String aadhar, String phone, String services, String accountType)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO customers (cardno, name, fname, dob, gender, email, marital, address, "
                    + "city, religion, category, income, education, occupation, pan, aadhar, phone, "
                    + "services, acctype, status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE')",
                Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, cardNo);
            ps.setString(i++, name);
            ps.setString(i++, fname);
            ps.setString(i++, dob);
            ps.setString(i++, gender);
            ps.setString(i++, email);
            ps.setString(i++, marital);
            ps.setString(i++, address);
            ps.setString(i++, city);
            ps.setString(i++, religion);
            ps.setString(i++, category);
            ps.setString(i++, income);
            ps.setString(i++, education);
            ps.setString(i++, occupation);
            ps.setString(i++, pan);
            ps.setString(i++, aadhar);
            ps.setString(i++, phone);
            ps.setString(i++, services);
            ps.setString(i, accountType);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public void updateContact(Connection c, String cardNo, String email, String phone,
            String address, String city) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE customers SET email = ?, phone = ?, address = ?, city = ? WHERE cardno = ?")) {
            ps.setString(1, email);
            ps.setString(2, phone);
            ps.setString(3, address);
            ps.setString(4, city);
            ps.setString(5, cardNo);
            ps.executeUpdate();
        }
    }

    public void setStatus(Connection c, String cardNo, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE customers SET status = ? WHERE cardno = ?")) {
            ps.setString(1, status);
            ps.setString(2, cardNo);
            ps.executeUpdate();
        }
    }

    public List<Customer> list(Connection c, String search, String status, int limit)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM customers WHERE 1 = 1");
        List<String> args = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (name LIKE ? OR cardno LIKE ? OR email LIKE ? OR phone LIKE ?)");
            String like = "%" + search.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ").append(Math.max(1, Math.min(limit, 500)));
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                ps.setString(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Customer> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        }
    }

    public long count(Connection c) throws SQLException {
        return AccountDao.scalar(c, "SELECT COUNT(*) FROM customers");
    }

    public long countByStatus(Connection c, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM customers WHERE status = ?")) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static Customer map(ResultSet rs) throws SQLException {
        return new Customer(
            rs.getLong("id"),
            Rows.str(rs, "cardno"),
            Rows.str(rs, "name"),
            Rows.str(rs, "fname"),
            Rows.str(rs, "dob"),
            Rows.str(rs, "gender"),
            Rows.str(rs, "email"),
            Rows.str(rs, "marital"),
            Rows.str(rs, "address"),
            Rows.str(rs, "city"),
            Rows.str(rs, "religion"),
            Rows.str(rs, "category"),
            Rows.str(rs, "income"),
            Rows.str(rs, "education"),
            Rows.str(rs, "occupation"),
            Rows.str(rs, "pan"),
            Rows.str(rs, "aadhar"),
            Rows.str(rs, "phone"),
            Rows.str(rs, "services"),
            Rows.str(rs, "acctype"),
            Rows.str(rs, "status"),
            Rows.time(rs, "created_at"));
    }
}
