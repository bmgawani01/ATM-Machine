package atm.core.dao;

import atm.core.Passwords;
import atm.core.Rows;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** Back-office users, used by the admin side of the app. */
public class AdminUserDao {

    public Admin find(Connection c, String username) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT username, full_name, role, pin_hash, pin_salt, status, last_login, "
                    + "failed_attempts, locked_until FROM admin_users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Admin(
                    Rows.str(rs, "username"),
                    Rows.str(rs, "full_name"),
                    Rows.str(rs, "role"),
                    Rows.str(rs, "pin_hash"),
                    Rows.integer(rs, "failed_attempts"),
                    Rows.time(rs, "locked_until"),
                    Rows.str(rs, "status"),
                    Rows.time(rs, "last_login"));
            }
        }
    }

    public boolean pinMatches(Admin admin, String pin) {
        return Passwords.verify(pin, admin.pinHash());
    }

    public void recordSuccess(Connection c, String username) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE admin_users SET last_login = NOW(), failed_attempts = 0, locked_until = NULL "
                    + "WHERE username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        }
    }

    public int incrementFailed(Connection c, String username) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE admin_users SET failed_attempts = failed_attempts + 1 WHERE username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT failed_attempts FROM admin_users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void lockUntil(Connection c, String username, LocalDateTime until) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE admin_users SET locked_until = ? WHERE username = ?")) {
            ps.setTimestamp(1, until == null ? null : Timestamp.valueOf(until));
            ps.setString(2, username);
            ps.executeUpdate();
        }
    }

    public void setStatus(Connection c, String username, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE admin_users SET status = ? WHERE username = ?")) {
            ps.setString(1, status);
            ps.setString(2, username);
            ps.executeUpdate();
        }
    }

    public void changePin(Connection c, String username, String newPin) throws SQLException {
        String salt = Passwords.newSalt();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE admin_users SET pin_hash = ?, pin_salt = ?, failed_attempts = 0, "
                    + "locked_until = NULL WHERE username = ?")) {
            ps.setString(1, Passwords.hash(newPin, salt));
            ps.setString(2, salt);
            ps.setString(3, username);
            ps.executeUpdate();
        }
    }

    /** Back-office user. The PIN hash is never exposed outside the service layer. */
    public record Admin(
        String username,
        String fullName,
        String role,
        String pinHash,
        int failedAttempts,
        LocalDateTime lockedUntil,
        String status,
        LocalDateTime lastLogin) {

        public boolean isLocked(LocalDateTime now) {
            return lockedUntil != null && lockedUntil.isAfter(now);
        }

        public boolean isActive() {
            return "ACTIVE".equalsIgnoreCase(status);
        }
    }
}
