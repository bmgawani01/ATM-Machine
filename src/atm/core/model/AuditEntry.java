package atm.core.model;

import java.time.LocalDateTime;

/** One entry of the audit trail: who did what, when, and from where. */
public record AuditEntry(
    long id,
    String actor,
    String cardNo,
    String action,
    String details,
    String ip,
    LocalDateTime createdAt) {
}
