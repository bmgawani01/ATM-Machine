package atm.core.service;

import atm.core.AtmException;
import atm.core.Config;
import atm.core.Context;
import atm.core.Database;
import atm.core.Ids;
import atm.core.Log;
import atm.core.Passwords;
import atm.core.dao.AccountDao;
import atm.core.dao.AdminUserDao;
import atm.core.dao.AuditDao;
import atm.core.dao.CardDao;
import atm.core.dao.CustomerDao;
import atm.core.dao.SecurityDao;
import atm.core.model.Account;
import atm.core.model.AuthSession;
import atm.core.model.Card;
import atm.core.model.CardStatus;
import atm.core.model.Customer;
import atm.core.model.Session;
import java.time.LocalDateTime;

/**
 * Sign-in, the 3-attempt PIN lock, sign-out and session expiry.
 *
 * <p>Rules enforced here: an unknown card never says "wrong PIN" instead it says "card not
 * found" only for a genuine card, three wrong PINs lock the card for
 * {@code security.lock.minutes}, a blocked card cannot sign in, a legacy plain-text PIN is
 * upgraded to a PBKDF2 hash on first use, and every session has a server-side deadline that
 * slides forward on each action.
 */
public class AuthService {

    private final Database db = Database.get();
    private final CardDao cards = new CardDao();
    private final AccountDao accounts = new AccountDao();
    private final CustomerDao customers = new CustomerDao();
    private final SecurityDao security = new SecurityDao();
    private final AuditDao auditDao = new AuditDao();
    private final AdminUserDao admins = new AdminUserDao();
    private final AuditService audit = new AuditService();

    public int maxAttempts() {
        return Config.getInt(Config.Setting.MAX_PIN_ATTEMPTS, 3);
    }

    public int lockMinutes() {
        return Config.getInt(Config.Setting.LOCK_MINUTES, 5);
    }

    public int sessionTimeoutSeconds() {
        return Config.getInt(Config.Setting.SESSION_TIMEOUT_SECONDS, 120);
    }

    /**
     * @throws AtmException with LOCKED after the limit is reached, AUTHENTICATION for a bad
     *     PIN, BLOCKED for a blocked card, NOT_FOUND for an unknown card
     */
    public AuthSession signIn(String cardNo, String pin, String channel) {
        String card = cardNo == null ? "" : cardNo.trim();
        String secret = pin == null ? "" : pin.trim();
        if (card.isEmpty() || secret.isEmpty()) {
            throw new AtmException(AtmException.Reason.VALIDATION, "Enter your card number and PIN");
        }

        LocalDateTime now = LocalDateTime.now();
        Card card0 = db.read(c -> cards.find(c, card));
        if (card0 == null) {
            audit.failedLogin(card, "unknown card");
            throw new AtmException(AtmException.Reason.NOT_FOUND, "Card number not recognised");
        }
        if (card0.status() == CardStatus.BLOCKED) {
            audit.failedLogin(card, "card blocked");
            throw new AtmException(AtmException.Reason.BLOCKED,
                "This card is blocked. Please contact your branch.");
        }
        if (card0.isLocked(now)) {
            audit.failedLogin(card, "card locked");
            throw new AtmException(AtmException.Reason.LOCKED,
                "Card locked after " + maxAttempts() + " wrong PINs. Try again in "
                    + card0.attemptsLeft(maxAttempts()) + " minute(s).");
        }

        CardDao.Credentials cred = db.read(c -> cards.credentials(c, card));
        boolean correct = cred != null && Passwords.verify(secret, cred.pinHash());
        if (!correct) {
            int attempts = db.read(c -> cards.incrementFailedAttempts(c, card));
            audit.failedLogin(card, "wrong pin, attempt " + attempts);
            if (attempts >= maxAttempts()) {
                db.read(c -> {
                    cards.lockUntil(c, card, now.plusMinutes(lockMinutes()));
                    return null;
                });
                audit.log(AuditService.CARD_LOCKED, card,
                    "Locked for " + lockMinutes() + " minutes after " + attempts + " failed attempts");
                throw new AtmException(AtmException.Reason.LOCKED,
                    "Card locked after " + maxAttempts() + " wrong PINs. Try again in "
                        + lockMinutes() + " minutes.");
            }
            throw new AtmException(AtmException.Reason.AUTHENTICATION,
                "Incorrect PIN. " + (maxAttempts() - attempts) + " attempt(s) left before the card locks.");
        }

        // successful sign-in: clear the counter, upgrade a legacy plain-text PIN, open a session
        String sessionId = Ids.sessionId();
        LocalDateTime expiresAt = now.plusSeconds(sessionTimeoutSeconds());
        String channel0 = channel == null ? Context.channel() : channel;
        AuthSession result = db.tx(c -> {
            cards.resetFailedAttempts(c, card);
            Customer customer = customers.findByCardNo(c, card);
            Account account = accounts.findByCardNo(c, card);
            if (customer == null || account == null) {
                throw new AtmException(AtmException.Reason.NOT_FOUND,
                    "This card is not linked to an account yet");
            }
            if (!account.isActive()) {
                throw new AtmException(AtmException.Reason.BLOCKED,
                    "Account is " + account.status().toLowerCase());
            }
            security.createSession(c, sessionId, card, account.accountNo(), channel0, expiresAt);
            auditDao.log(c, Context.actor(), card, AuditService.SIGN_IN,
                "Signed in from " + Context.ip(), Context.ip());
            return new AuthSession(sessionId, customer, account, cards.find(c, card), expiresAt);
        });

        if (Passwords.isPlainText(cred.pinHash())) {
            db.read(c -> {
                String salt = Passwords.newSalt();
                try (var ps = c.prepareStatement(
                        "UPDATE cards SET pin_hash = ?, pin_salt = ? WHERE cardno = ?")) {
                    ps.setString(1, Passwords.hash(secret, salt));
                    ps.setString(2, salt);
                    ps.setString(3, card);
                    ps.executeUpdate();
                }
                return null;
            });
        }

        Log.info("Sign-in ok for " + Ids.maskCard(card) + " (session " + shortId(sessionId) + ")");
        return result;
    }

    /** A no-argument sign-in using the channel from {@link Context}. */
    public AuthSession signIn(String cardNo, String pin) {
        return signIn(cardNo, pin, Context.channel());
    }

    public void signOut(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        db.read(c -> {
            security.revokeSession(c, sessionId, "SIGNED_OUT");
            return null;
        });
    }

    public void signOutAllForCard(String cardNo, String reason) {
        db.read(c -> {
            security.revokeAllForCard(c, cardNo, reason);
            return null;
        });
    }

    /** True while the session is alive; also slides the idle deadline forward. */
    public boolean isSessionAlive(String sessionId) {
        Session s = db.read(c -> security.session(c, sessionId));
        if (s == null || s.isExpired(LocalDateTime.now())) {
            return false;
        }
        touch(sessionId);
        return true;
    }

    public void touch(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        LocalDateTime next = LocalDateTime.now().plusSeconds(sessionTimeoutSeconds());
        db.read(c -> {
            security.touchSession(c, sessionId, next);
            return null;
        });
    }

    public LocalDateTime expiresAt(String sessionId) {
        Session s = db.read(c -> security.session(c, sessionId));
        return s == null ? null : s.expiresAt();
    }

    public void requireSession(String sessionId) {
        if (!isSessionAlive(sessionId)) {
            throw new AtmException(AtmException.Reason.SESSION_EXPIRED,
                "Your session timed out. Please sign in again");
        }
    }

    public void purgeOldSessions() {
        int removed = db.read(security::purgeExpired);
        if (removed > 0) {
            Log.info("Purged " + removed + " expired session row(s)");
        }
    }

    // ------------------------------------------------------------ admin sign-in

    public AdminUserDao.Admin signInAdmin(String username, String pin) {
        String user = username == null ? "" : username.trim();
        String secret = pin == null ? "" : pin.trim();
        if (user.isEmpty() || secret.isEmpty()) {
            throw new AtmException(AtmException.Reason.VALIDATION, "Enter your staff ID and PIN");
        }
        LocalDateTime now = LocalDateTime.now();
        AdminUserDao.Admin admin = db.read(c -> admins.find(c, user));
        if (admin == null) {
            audit.failedLogin(user, "unknown staff id");
            throw new AtmException(AtmException.Reason.NOT_FOUND, "Staff ID not recognised");
        }
        if (!admin.isActive()) {
            throw new AtmException(AtmException.Reason.BLOCKED, "This staff account is disabled");
        }
        if (admin.isLocked(now)) {
            throw new AtmException(AtmException.Reason.LOCKED, "Staff account locked. Try again later.");
        }
        if (!admins.pinMatches(admin, secret)) {
            int attempts = db.read(c -> admins.incrementFailed(c, user));
            audit.failedLogin(user, "wrong staff pin, attempt " + attempts);
            if (attempts >= maxAttempts()) {
                db.read(c -> {
                    admins.lockUntil(c, user, now.plusMinutes(lockMinutes()));
                    return null;
                });
                throw new AtmException(AtmException.Reason.LOCKED, "Staff account locked");
            }
            throw new AtmException(AtmException.Reason.AUTHENTICATION,
                "Incorrect PIN. " + (maxAttempts() - attempts) + " attempt(s) left.");
        }
        db.read(c -> {
            admins.recordSuccess(c, user);
            auditDao.log(c, user, null, AuditService.ADMIN_SIGN_IN, "Back office sign-in",
                Context.ip());
            return null;
        });
        Log.info("Admin sign-in ok: " + user);
        return admin;
    }

    private static String shortId(String id) {
        return id == null ? "-" : id.substring(0, Math.min(8, id.length()));
    }
}
