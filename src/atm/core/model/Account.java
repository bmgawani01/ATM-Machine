package atm.core.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A bank account. The balance is a stored, updated value; the transactions table is the history. */
public record Account(
    String accountNo,
    String cardNo,
    AccountType type,
    BigDecimal balance,
    String status,
    String currency,
    LocalDateTime openedAt) {

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean isFrozen() {
        return "FROZEN".equalsIgnoreCase(status);
    }

    public String maskedAccountNo() {
        if (accountNo == null || accountNo.length() < 4) {
            return "XXXX-XXXX-XXXX";
        }
        return "XXXX-XXXX-" + accountNo.substring(accountNo.length() - 4);
    }
}
