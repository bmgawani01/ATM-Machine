package atm.core.dao;

import atm.core.Passwords;
import atm.core.Rows;
import atm.core.model.Card;
import atm.core.model.CardStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Cards, hashed PINs and the 3-attempt lock state. */
public class CardDao {

    private static final String COLUMNS =
        "cardno, account_no, status, failed_attempts, locked_until, expires_on, issued_at, last_used_at";

    public Card find(Connection c, String cardNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM cards WHERE cardno = ?")) {
            ps.setString(1, cardNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public void insert(Connection c, String cardNo, String accountNo, String pinHash,
            String salt, LocalDate expiresOn) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO cards (cardno, account_no, pin_hash, pin_salt, status, expires_on) "
                    + "VALUES (?, ?, ?, ?, 'ACTIVE', ?)")) {
            ps.setString(1, cardNo);
            ps.setString(2, accountNo);
            ps.setString(3, pinHash);
            ps.setString(4, salt);
            ps.setDate(5, expiresOn == null ? null : java.sql.Date.valueOf(expiresOn));
            ps.executeUpdate();
        }
    }

    /** The stored hash and salt, only needed by the sign-in and PIN-change code. */
    public Credentials credentials(Connection c, String cardNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT pin_hash, pin_salt FROM cards WHERE cardno = ?")) {
            ps.setString(1, cardNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Credentials(Rows.str(rs, "pin_hash"), Rows.str(rs, "pin_salt"));
            }
        }
    }

    public boolean pinMatches(Connection c, String cardNo, String pin) throws SQLException {
        Credentials cred = credentials(c, cardNo);
        return cred != null && Passwords.verify(pin, cred.pinHash());
    }

    public void changePin(Connection c, String cardNo, String newPin) throws SQLException {
        String salt = Passwords.newSalt();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE cards SET pin_hash = ?, pin_salt = ?, failed_attempts = 0, "
                    + "locked_until = NULL WHERE cardno = ?")) {
            ps.setString(1, Passwords.hash(newPin, salt));
            ps.setString(2, salt);
            ps.setString(3, cardNo);
            ps.executeUpdate();
        }
    }

    public int incrementFailedAttempts(Connection c, String cardNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE cards SET failed_attempts = failed_attempts + 1 WHERE cardno = ?")) {
            ps.setString(1, cardNo);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT failed_attempts FROM cards WHERE cardno = ?")) {
            ps.setString(1, cardNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void resetFailedAttempts(Connection c, String cardNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE cards SET failed_attempts = 0, locked_until = NULL, last_used_at = NOW() "
                    + "WHERE cardno = ?")) {
            ps.setString(1, cardNo);
            ps.executeUpdate();
        }
    }

    public void lockUntil(Connection c, String cardNo, LocalDateTime until) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE cards SET locked_until = ? WHERE cardno = ?")) {
            ps.setTimestamp(1, until == null ? null : Timestamp.valueOf(until));
            ps.setString(2, cardNo);
            ps.executeUpdate();
        }
    }

    public void setStatus(Connection c, String cardNo, CardStatus status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE cards SET status = ? WHERE cardno = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, cardNo);
            ps.executeUpdate();
        }
    }

    /** Clearing a lock also resets the counter, otherwise the next wrong PIN locks it again. */
    public void unblock(Connection c, String cardNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE cards SET status = 'ACTIVE', failed_attempts = 0, locked_until = NULL "
                    + "WHERE cardno = ?")) {
            ps.setString(1, cardNo);
            ps.executeUpdate();
        }
    }

    public void touch(Connection c, String cardNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE cards SET last_used_at = NOW() WHERE cardno = ?")) {
            ps.setString(1, cardNo);
            ps.executeUpdate();
        }
    }

    public void setExpiry(Connection c, String cardNo, LocalDate expiresOn) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE cards SET expires_on = ? WHERE cardno = ?")) {
            ps.setDate(1, expiresOn == null ? null : java.sql.Date.valueOf(expiresOn));
            ps.setString(2, cardNo);
            ps.executeUpdate();
        }
    }

    public List<Card> list(Connection c, String search, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM cards WHERE 1 = 1";
        if (search != null && !search.isBlank()) {
            sql += " AND (cardno LIKE ? OR account_no LIKE ?)";
        }
        sql += " ORDER BY issued_at DESC LIMIT " + Math.max(1, Math.min(limit, 500));
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim() + "%";
                ps.setString(1, like);
                ps.setString(2, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Card> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        }
    }

    public long count(Connection c, CardStatus status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM cards WHERE status = ?")) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static Card map(ResultSet rs) throws SQLException {
        return new Card(
            Rows.str(rs, "cardno"),
            Rows.str(rs, "account_no"),
            CardStatus.of(Rows.str(rs, "status")),
            Rows.integer(rs, "failed_attempts"),
            Rows.time(rs, "locked_until"),
            Rows.dateOnly(rs, "expires_on"),
            Rows.time(rs, "issued_at"),
            Rows.time(rs, "last_used_at"));
    }

    /** PIN hash plus the salt it was made with. */
    public record Credentials(String pinHash, String salt) {
    }
}
