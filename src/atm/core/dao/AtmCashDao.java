package atm.core.dao;

import atm.core.Money;
import atm.core.Rows;
import atm.core.model.AtmCash;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The cash float of the machine. Withdrawals only succeed while enough notes of each
 * denomination remain, which is what stops an ATM from promising money it does not have.
 */
public class AtmCashDao {

    public List<AtmCash> levels(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT denom, notes, updated_at FROM atm_cash ORDER BY denom DESC");
             ResultSet rs = ps.executeQuery()) {
            List<AtmCash> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new AtmCash(
                    Rows.integer(rs, "denom"),
                    Rows.integer(rs, "notes"),
                    Rows.time(rs, "updated_at")));
            }
            return out;
        }
    }

    public BigDecimal total(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(denom * notes), 0) FROM atm_cash");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Money.scale(rs.getBigDecimal(1)) : Money.ZERO;
        }
    }

    public int[] denominations(Connection c) throws SQLException {
        List<AtmCash> levels = levels(c);
        int[] out = new int[levels.size()];
        for (int i = 0; i < levels.size(); i++) {
            out[i] = levels.get(i).denom();
        }
        return out;
    }

    /** Removes notes; returns false when the machine does not hold enough of a denomination. */
    public boolean dispense(Connection c, int[] denom, int[] counts) throws SQLException {
        for (int i = 0; i < denom.length; i++) {
            if (counts[i] <= 0) {
                continue;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE atm_cash SET notes = notes - ?, updated_at = NOW() "
                        + "WHERE denom = ? AND notes >= ?")) {
                ps.setInt(1, counts[i]);
                ps.setInt(2, denom[i]);
                ps.setInt(3, counts[i]);
                if (ps.executeUpdate() != 1) {
                    return false;
                }
            }
        }
        return true;
    }

    public void restock(Connection c, int denom, int notes) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE atm_cash SET notes = notes + ?, updated_at = NOW() WHERE denom = ?")) {
            ps.setInt(1, notes);
            ps.setInt(2, denom);
            ps.executeUpdate();
        }
    }

    public void setLevel(Connection c, int denom, int notes) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE atm_cash SET notes = ?, updated_at = NOW() WHERE denom = ?")) {
            ps.setInt(1, notes);
            ps.setInt(2, denom);
            ps.executeUpdate();
        }
    }
}
