package atm.core.model;

import java.time.LocalDateTime;

/** Everything the app needs after a successful sign-in. */
public record AuthSession(
    String sessionId,
    Customer customer,
    Account account,
    Card card,
    LocalDateTime expiresAt) {

    public String cardNo() {
        return customer.cardNo();
    }
}
