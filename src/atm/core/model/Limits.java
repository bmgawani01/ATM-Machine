package atm.core.model;

import java.math.BigDecimal;

/** Per-account limits; {@code null} means "fall back to the app-wide default". */
public record Limits(
    BigDecimal perTxn,
    BigDecimal dailyWithdrawal,
    Integer dailyTransfers,
    Integer dailyBillPayments,
    BigDecimal requireOtpAbove) {

    public boolean requiresOtp(BigDecimal amount) {
        return requireOtpAbove != null && amount.compareTo(requireOtpAbove) >= 0;
    }
}
