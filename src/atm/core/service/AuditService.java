package atm.core.service;

import atm.core.Context;
import atm.core.Database;
import atm.core.dao.AuditDao;
import atm.core.model.AuditEntry;
import atm.core.model.FailedLogin;
import java.util.List;

/** Writes the audit trail. Every sensitive action in the app goes through here. */
public class AuditService {

    /** Action names, so the audit search and the reports agree on spelling. */
    public static final String SIGN_IN = "SIGN_IN";
    public static final String SIGN_IN_FAILED = "SIGN_IN_FAILED";
    public static final String CARD_LOCKED = "CARD_LOCKED";
    public static final String SIGN_OUT = "SIGN_OUT";
    public static final String SESSION_TIMEOUT = "SESSION_TIMEOUT";
    public static final String DEPOSIT = "DEPOSIT";
    public static final String WITHDRAWAL = "WITHDRAWAL";
    public static final String FAST_CASH = "FAST_CASH";
    public static final String TRANSFER = "TRANSFER";
    public static final String BILL_PAYMENT = "BILL_PAYMENT";
    public static final String AIRTIME = "AIRTIME";
    public static final String PIN_CHANGE = "PIN_CHANGE";
    public static final String CARD_BLOCKED = "CARD_BLOCKED";
    public static final String CARD_UNBLOCKED = "CARD_UNBLOCKED";
    public static final String OTP_ISSUED = "OTP_ISSUED";
    public static final String OTP_FAILED = "OTP_FAILED";
    public static final String DUPLICATE_BLOCKED = "DUPLICATE_BLOCKED";
    public static final String LIMIT_BLOCKED = "LIMIT_BLOCKED";
    public static final String SIGNUP = "SIGNUP";
    public static final String ADMIN_SIGN_IN = "ADMIN_SIGN_IN";
    public static final String ADMIN_ACTION = "ADMIN_ACTION";

    private final Database db = Database.get();
    private final AuditDao dao = new AuditDao();

    public void log(String action, String cardNo, String details) {
        log(action, cardNo, details, null);
    }

    public void log(String action, String cardNo, String details, String actorOverride) {
        try {
            db.read(c -> {
                dao.log(c, actorOverride == null ? Context.actor() : actorOverride, cardNo, action,
                    details, Context.ip());
                return null;
            });
        } catch (RuntimeException e) {
            // auditing must never block a customer's transaction
            atm.core.Log.errorQuiet("Audit write failed for " + action, e);
        }
    }

    public void failedLogin(String cardNo, String reason) {
        try {
            db.read(c -> {
                dao.failedLogin(c, cardNo, reason, Context.ip());
                dao.log(c, Context.actor(), cardNo, SIGN_IN_FAILED, reason, Context.ip());
                return null;
            });
        } catch (RuntimeException e) {
            atm.core.Log.errorQuiet("Failed-login write failed", e);
        }
    }

    public List<AuditEntry> recent(int limit) {
        return db.read(c -> dao.recent(c, limit));
    }

    public List<AuditEntry> recent(String action, String cardNo, int limit) {
        return db.read(c -> dao.recent(c, action, cardNo, limit));
    }

    public List<FailedLogin> failedLogins(int limit) {
        return db.read(c -> dao.failedLogins(c, limit));
    }
}
