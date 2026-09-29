package atm.core.dao;

import atm.core.Rows;
import atm.core.model.OtpChallenge;
import atm.core.model.Session;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** One-time codes and server-side sessions. */
public class SecurityDao {

    // ------------------------------------------------------------------ OTP

    public long createOtp(Connection c, String cardNo, String purpose, String codeHash,
            String salt, LocalDateTime expiresAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO otp_challenges (cardno, purpose, code_hash, code_salt, expires_at) "
                    + "VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cardNo);
            ps.setString(2, purpose);
            ps.setString(3, codeHash);
            ps.setString(4, salt);
            ps.setTimestamp(5, Timestamp.valueOf(expiresAt));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public OtpChallenge latestOtp(Connection c, String cardNo, String purpose) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, cardno, purpose, expires_at, consumed_at, attempts FROM otp_challenges "
                    + "WHERE cardno = ? AND purpose = ? AND consumed_at IS NULL "
                    + "ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, cardNo);
            ps.setString(2, purpose);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapOtp(rs) : null;
            }
        }
    }

    public String otpHash(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT code_hash FROM otp_challenges WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Rows.str(rs, "code_hash") : null;
            }
        }
    }

    public void consumeOtp(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE otp_challenges SET consumed_at = NOW() WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public int incrementOtpAttempts(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE otp_challenges SET attempts = attempts + 1 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT attempts FROM otp_challenges WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void invalidateOtps(Connection c, String cardNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE otp_challenges SET consumed_at = NOW() "
                    + "WHERE cardno = ? AND consumed_at IS NULL")) {
            ps.setString(1, cardNo);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------- sessions

    public void createSession(Connection c, String id, String cardNo, String accountNo,
            String channel, LocalDateTime expiresAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sessions (id, cardno, account_no, channel, expires_at) "
                    + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, cardNo);
            ps.setString(3, accountNo);
            ps.setString(4, channel);
            ps.setTimestamp(5, Timestamp.valueOf(expiresAt));
            ps.executeUpdate();
        }
    }

    public Session session(Connection c, String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return null;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, cardno, account_no, channel, created_at, last_seen_at, expires_at, "
                    + "revoked FROM sessions WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapSession(rs) : null;
            }
        }
    }

    /** Slides the idle deadline forward on every action the customer performs. */
    public void touchSession(Connection c, String id, LocalDateTime expiresAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE sessions SET last_seen_at = NOW(), expires_at = ? "
                    + "WHERE id = ? AND revoked = 0")) {
            ps.setTimestamp(1, Timestamp.valueOf(expiresAt));
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    public void revokeSession(Connection c, String id, String reason) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE sessions SET revoked = 1, revoked_reason = ? WHERE id = ?")) {
            ps.setString(1, reason);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    public void revokeAllForCard(Connection c, String cardNo, String reason) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE sessions SET revoked = 1, revoked_reason = ? "
                    + "WHERE cardno = ? AND revoked = 0")) {
            ps.setString(1, reason);
            ps.setString(2, cardNo);
            ps.executeUpdate();
        }
    }

    public int purgeExpired(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM sessions WHERE expires_at < NOW() - INTERVAL 1 DAY")) {
            return ps.executeUpdate();
        }
    }

    public List<Session> activeSessions(Connection c, int limit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, cardno, account_no, channel, created_at, last_seen_at, expires_at, "
                    + "revoked FROM sessions WHERE revoked = 0 AND expires_at > NOW() "
                    + "ORDER BY last_seen_at DESC LIMIT " + Math.max(1, Math.min(limit, 200)))) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Session> out = new java.util.ArrayList<>();
                while (rs.next()) {
                    out.add(mapSession(rs));
                }
                return out;
            }
        }
    }

    private static OtpChallenge mapOtp(ResultSet rs) throws SQLException {
        return new OtpChallenge(
            rs.getLong("id"),
            Rows.str(rs, "cardno"),
            Rows.str(rs, "purpose"),
            Rows.time(rs, "expires_at"),
            Rows.time(rs, "consumed_at"),
            Rows.integer(rs, "attempts"));
    }

    private static Session mapSession(ResultSet rs) throws SQLException {
        return new Session(
            Rows.str(rs, "id"),
            Rows.str(rs, "cardno"),
            Rows.str(rs, "account_no"),
            Rows.str(rs, "channel"),
            Rows.time(rs, "created_at"),
            Rows.time(rs, "last_seen_at"),
            Rows.time(rs, "expires_at"),
            Rows.integer(rs, "revoked") != 0);
    }
}
