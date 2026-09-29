package atm.core.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row of {@code transactions}: the bank's ledger. */
public record Txn(
    long id,
    String accountNo,
    TransactionType type,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String channel,
    String note,
    String reference,
    String idempotencyKey,
    String status,
    LocalDateTime createdAt) {

    public boolean isCredit() {
        return !type.isDebit();
    }

    public String signedAmount() {
        return (isCredit() ? "+" : "-") + amount.toPlainString();
    }
}
