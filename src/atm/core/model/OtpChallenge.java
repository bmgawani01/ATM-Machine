package atm.core.model;

import java.time.LocalDateTime;

/** A one-time code issued for a sensitive operation (transfer, PIN change, card block). */
public record OtpChallenge(
    long id,
    String cardNo,
    String purpose,
    LocalDateTime expiresAt,
    LocalDateTime consumedAt,
    int attempts) {

    public boolean isUsable(LocalDateTime now) {
        return consumedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
