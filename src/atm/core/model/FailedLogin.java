package atm.core.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A rejected sign-in, for the failed-login monitoring screen. */
public record FailedLogin(
    long id,
    String cardNo,
    String reason,
    String ip,
    LocalDateTime createdAt) {
}
