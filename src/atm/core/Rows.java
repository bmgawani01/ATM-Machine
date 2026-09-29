package atm.core;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Small helpers for reading JDBC columns. Everything coming out of the database is
 * nullable, so all of these tolerate NULL and fall back to a sensible default instead of
 * throwing in the middle of a UI action.
 */
public final class Rows {

    private Rows() {
    }

    public static String str(ResultSet rs, String column) throws SQLException {
        String v = rs.getString(column);
        return v == null ? "" : v;
    }

    public static String strOrNull(ResultSet rs, String column) throws SQLException {
        return rs.getString(column);
    }

    public static int integer(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? 0 : v;
    }

    public static boolean bool(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return !rs.wasNull() && v != 0;
    }

    public static BigDecimal money(ResultSet rs, String column) throws SQLException {
        BigDecimal v = rs.getBigDecimal(column);
        return Money.scale(v);
    }

    public static BigDecimal moneyOrNull(ResultSet rs, String column) throws SQLException {
        BigDecimal v = rs.getBigDecimal(column);
        return v == null ? null : Money.scale(v);
    }

    public static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    public static LocalDate date(ResultSet rs, String column) throws SQLException {
        return dateOnly(rs, column);
    }

    public static LocalDate dateOnly(ResultSet rs, String column) throws SQLException {
        java.sql.Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }
}
