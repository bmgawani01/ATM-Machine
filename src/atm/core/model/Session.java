package atm.core.model;

import java.time.LocalDateTime;

/** Server-side session, so a stolen desktop cannot stay logged in forever. */
public record Session(
    String id,
    String cardNo,
    String accountNo,
    String channel,
    LocalDateTime createdAt,
    LocalDateTime lastSeenAt,
    LocalDateTime expiresAt,
    boolean revoked) {

    public boolean isExpired(LocalDateTime now) {
        return revoked || expiresAt == null || !expiresAt.isAfter(now);
    }
}
