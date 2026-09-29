package atm.core.service;

import atm.core.AtmException;
import atm.core.Config;
import atm.core.Database;
import atm.core.Log;
import atm.core.Passwords;
import atm.core.dao.SecurityDao;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * One-time codes for sensitive operations (fund transfer, PIN change, card block).
 *
 * <p>The code is generated on the server, stored only as a PBKDF2 hash, expires after
 * {@code security.otp.ttl.seconds} and can be used once. Delivery is pluggable: in this
 * project the code goes to the console / dialog (there is no SMS gateway here), which is
 * why {@link #issue} hands it back to the caller. A real deployment would send it by SMS or
 * e-mail and return nothing.
 */
public class OtpService {

    public static final String PURPOSE_TRANSFER = "TRANSFER";
    public static final String PURPOSE_PIN_CHANGE = "PIN_CHANGE";
    public static final String PURPOSE_CARD_BLOCK = "CARD_BLOCK";

    private final Database db = Database.get();
    private final SecurityDao dao = new SecurityDao();
    private final AuditService audit = new AuditService();

    /** @return the code, for delivery to the customer (console/dialog in this build) */
    public String issue(String cardNo, String purpose) {
        String code = String.valueOf(Passwords.numericCode());
        String salt = Passwords.newSalt();
        int ttl = Config.getInt(Config.Setting.OTP_TTL_SECONDS, 120);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ttl);
        db.read(c -> {
            dao.invalidateOtps(c, cardNo);
            dao.createOtp(c, cardNo, purpose, Passwords.hash(code, salt), salt, expiresAt);
            return null;
        });
        audit.log(AuditService.OTP_ISSUED, cardNo, purpose + " code issued, valid " + ttl + "s");
        Log.info("OTP for " + purpose + " issued to card " + atm.core.Ids.maskCard(cardNo)
            + " (delivery=" + Config.get(Config.Setting.OTP_DELIVERY) + ")");
        return code;
    }

    public boolean verify(String cardNo, String purpose, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        Long id = db.read(c -> {
            var challenge = dao.latestOtp(c, cardNo, purpose);
            if (challenge == null || !challenge.isUsable(LocalDateTime.now())) {
                return null;
            }
            String hash = dao.otpHash(c, challenge.id());
            if (hash != null && Passwords.verify(code.trim(), hash)) {
                dao.consumeOtp(c, challenge.id());
                return challenge.id();
            }
            dao.incrementOtpAttempts(c, challenge.id());
            return -1L;
        });

        boolean ok = id != null && id > 0;
        if (!ok) {
            audit.log(AuditService.OTP_FAILED, cardNo, "wrong code for " + purpose);
        }
        return ok;
    }

    /** Throws the friendly error the customer sees when the code is wrong. */
    public void require(String cardNo, String purpose, String code) {
        if (!verify(cardNo, purpose, code)) {
            throw new AtmException(AtmException.Reason.OTP_INVALID,
                "Invalid or expired verification code");
        }
    }

    public static String describe(String purpose) {
        switch (purpose.toUpperCase(Locale.ROOT)) {
            case PURPOSE_TRANSFER:
                return "fund transfer";
            case PURPOSE_PIN_CHANGE:
                return "PIN change";
            case PURPOSE_CARD_BLOCK:
                return "card blocking";
            default:
                return "this operation";
        }
    }
}
