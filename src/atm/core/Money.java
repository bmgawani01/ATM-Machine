package atm.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Locale;

/** Money helpers: the whole app stores and calculates in {@link BigDecimal}, never double. */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DecimalFormat USD = new DecimalFormat("#,##0.00", symbols());
    private static final DecimalFormat PLAIN = new DecimalFormat("#,##0.##", symbols());

    private Money() {
    }

    private static java.text.DecimalFormatSymbols symbols() {
        java.text.DecimalFormatSymbols s = new java.text.DecimalFormatSymbols(Locale.US);
        s.setGroupingSeparator(',');
        s.setDecimalSeparator('.');
        return s;
    }

    public static BigDecimal of(String text) {
        if (text == null) {
            throw new AtmException.Validation("Enter an amount");
        }
        String t = text.trim().replace(",", "").replace("$", "").replace("USD", "").trim();
        if (t.isEmpty()) {
            throw new AtmException.Validation("Enter an amount");
        }
        BigDecimal v;
        try {
            v = new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new AtmException.Validation("'" + text.trim() + "' is not a valid amount");
        }
        if (v.scale() > 2) {
            throw new AtmException.Validation("Amount can only have 2 decimal places");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal positive(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AtmException.Validation("Amount must be greater than 0");
        }
        return scale(v);
    }

    public static String usd(BigDecimal v) {
        return "USD " + PLAIN.format(scale(v));
    }

    public static String usdExact(BigDecimal v) {
        return "USD " + USD.format(scale(v));
    }

    /** Plain number, no currency and no grouping - used by reports and CSV exports. */
    public static String plain(BigDecimal v) {
        return scale(v).toPlainString();
    }

    /** Notes of each denomination needed to dispense {@code amount}, largest first. */
    public static String noteBreakdown(BigDecimal amount, int[] denominations) {
        StringBuilder sb = new StringBuilder();
        long remaining = scale(amount).movePointRight(2).longValue();
        int[] d = denominations.clone();
        java.util.Arrays.sort(d);
        for (int i = d.length - 1; i >= 0 && remaining > 0; i--) {
            long value = d[i] * 100L;
            if (value <= 0 || value > remaining) {
                continue;
            }
            long count = remaining / value;
            remaining -= count * value;
            if (count > 0) {
                sb.append(count).append(" x ").append(d[i]).append("   ");
            }
        }
        return sb.toString().trim();
    }
}
