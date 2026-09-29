package atm.core.model;

import java.math.BigDecimal;

/** Headline numbers for the admin dashboard. */
public record DashboardStats(
    long customers,
    long accounts,
    long activeCards,
    long blockedCards,
    long transactionsToday,
    BigDecimal volumeToday,
    BigDecimal depositsToday,
    BigDecimal withdrawalsToday,
    long failedLoginsToday,
    BigDecimal atmCashOnHand) {
}
