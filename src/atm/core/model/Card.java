package atm.core.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** The customer's ATM card plus the state of the 3-attempt PIN lock. */
public record Card(
    String cardNo,
    String accountNo,
    CardStatus status,
    int failedAttempts,
    LocalDateTime lockedUntil,
    LocalDate expiresOn,
    LocalDateTime issuedAt,
    LocalDateTime lastUsedAt) {

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public int attemptsLeft(int maxAttempts) {
        return Math.max(0, maxAttempts - failedAttempts);
    }
}
