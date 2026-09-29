package atm.core.dao;

import atm.core.Rows;
import atm.core.model.AuditEntry;
import atm.core.model.FailedLogin;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Audit trail and failed sign-in log. */
public class AuditDao {

    public void log(Connection c, String actor, String cardNo, String action, String details,
            String ip) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO audit_log (actor, cardno, action, details, ip) VALUES (?,?,?,?,?)")) {
            ps.setString(1, actor);
            ps.setString(2, cardNo);
            ps.setString(3, action);
            ps.setString(4, truncate(details));
            ps.setString(5, ip);
            ps.executeUpdate();
        }
    }

    public void failedLogin(Connection c, String cardNo, String reason, String ip)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO failed_logins (cardno, reason, ip) VALUES (?,?,?)")) {
            ps.setString(1, cardNo);
            ps.setString(2, reason);
            ps.setString(3, ip);
            ps.executeUpdate();
        }
    }

    public List<AuditEntry> recent(Connection c, int limit) throws SQLException {
        return recent(c, null, null, limit);
    }

    public List<AuditEntry> recent(Connection c, String action, String cardNo, int limit)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT id, actor, cardno, action, details, ip, created_at FROM audit_log WHERE 1 = 1");
        List<String> args = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            args.add(action);
        }
        if (cardNo != null && !cardNo.isBlank()) {
            sql.append(" AND cardno = ?");
            args.add(cardNo);
        }
        sql.append(" ORDER BY id DESC LIMIT ").append(Math.max(1, Math.min(limit, 500)));
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                ps.setString(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<AuditEntry> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new AuditEntry(
                        rs.getLong("id"),
                        Rows.str(rs, "actor"),
                        Rows.str(rs, "cardno"),
                        Rows.str(rs, "action"),
                        Rows.str(rs, "details"),
                        Rows.str(rs, "ip"),
                        Rows.time(rs, "created_at")));
                }
                return out;
            }
        }
    }

    public List<FailedLogin> failedLogins(Connection c, int limit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, cardno, reason, ip, created_at FROM failed_logins "
                    + "ORDER BY id DESC LIMIT " + Math.max(1, Math.min(limit, 500)))) {
            try (ResultSet rs = ps.executeQuery()) {
                List<FailedLogin> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new FailedLogin(
                        rs.getLong("id"),
                        Rows.str(rs, "cardno"),
                        Rows.str(rs, "reason"),
                        Rows.str(rs, "ip"),
                        Rows.time(rs, "created_at")));
                }
                return out;
            }
        }
    }

    public long failedSince(Connection c, LocalDateTime since, String cardNo) throws SQLException {
        String sql = "SELECT COUNT(*) FROM failed_logins WHERE created_at >= ?"
            + (cardNo == null ? "" : " AND cardno = ?");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(since));
            if (cardNo != null) {
                ps.setString(2, cardNo);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    public long countByAction(Connection c, String action, LocalDateTime since) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM audit_log WHERE action = ? AND created_at >= ?")) {
            ps.setString(1, action);
            ps.setTimestamp(2, Timestamp.valueOf(since));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    public long count(Connection c) throws SQLException {
        return AccountDao.scalar(c, "SELECT COUNT(*) FROM audit_log");
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 255 ? s : s.substring(0, 252) + "...";
    }
}
