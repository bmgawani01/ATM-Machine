package atm.core.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A customer profile, one row of {@code customers}. */
public record Customer(
    long id,
    String cardNo,
    String name,
    String fname,
    String dob,
    String gender,
    String email,
    String marital,
    String address,
    String city,
    String religion,
    String category,
    String income,
    String education,
    String occupation,
    String pan,
    String aadhar,
    String phone,
    String services,
    String accountType,
    String status,
    LocalDateTime createdAt) {

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public String initials() {
        if (name == null || name.isBlank()) {
            return "ATM";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }
}
