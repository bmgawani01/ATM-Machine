package atm.core.model;

import java.math.BigDecimal;

/**
 * Outcome of a money movement. {@code duplicate} is true when the idempotency key was
 * already used, meaning the app refused to charge the customer twice and returned the
 * original result instead.
 */
public record MovementResult(
    Txn txn,
    BigDecimal balance,
    String message,
    boolean duplicate) {
}
