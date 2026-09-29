package atm.core;

import atm.core.model.AuthSession;
import atm.core.model.Biller;
import atm.core.model.Customer;
import atm.core.model.Limits;
import atm.core.model.MovementResult;
import atm.core.model.Txn;
import atm.core.model.TransactionType;
import atm.core.service.AdminService;
import atm.core.service.AuditService;
import atm.core.service.AuthService;
import atm.core.service.CardService;
import atm.core.service.MoneyService;
import atm.core.service.OnboardingService;
import atm.core.service.OtpService;
import atm.core.service.SessionManager;
import atm.core.service.StatementService;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The one door into the bank. Screens, the command line checks and the web tier all go
 * through this object, so a rule like "the account number always comes from the session"
 * is enforced in one place instead of in every dialog.
 *
 * <pre>
 * Bank bank = Bank.get();
 * Bank.CustomerSession session = bank.signIn(cardNo, pin, "ATM");
 * bank.withdraw(session, "200", "cash", Ids.idempotencyKey());
 * bank.signOut(session);
 * </pre>
 */
public final class Bank {

    /** A signed-in customer, and the only account number the screens ever use. */
    public record CustomerSession(String sessionId, String cardNo, String accountNo, String name,
            LocalDateTime expiresAt) {
    }

    private static final Bank INSTANCE = new Bank();

    public static Bank get() {
        return INSTANCE;
    }

    private final AuthService auth = new AuthService();
    private final MoneyService money = new MoneyService();
    private final CardService cards = new CardService();
    private final OnboardingService onboarding = new OnboardingService();
    private final OtpService otp = new OtpService();
    private final StatementService statements = new StatementService();
    private final AuditService audit = new AuditService();
    private final AdminService admin = new AdminService();
    private final SessionManager sessions = new SessionManager();

    private Bank() {
    }

    public AuthService auth() {
        return auth;
    }

    public MoneyService money() {
        return money;
    }

    public CardService cards() {
        return cards;
    }

    public OnboardingService onboarding() {
        return onboarding;
    }

    public OtpService otp() {
        return otp;
    }

    public StatementService statements() {
        return statements;
    }

    public AuditService audit() {
        return audit;
    }

    public AdminService admin() {
        return admin;
    }

    public SessionManager sessions() {
        return sessions;
    }

    // ----------------------------------------------------------------- sign in

    public CustomerSession signIn(String cardNo, String pin, String channel) {
        AuthSession s = auth.signIn(cardNo, pin, channel);
        Context.set(channel, s.cardNo(), Context.ip());
        sessions.opened(s.sessionId());
        sessions.start();
        return new CustomerSession(s.sessionId(), s.cardNo(), s.account().accountNo(),
            s.customer().name(), s.expiresAt());
    }

    public CustomerSession signIn(String cardNo, String pin) {
        return signIn(cardNo, pin, Context.CHANNEL_DESKTOP);
    }

    public void signOut(CustomerSession session) {
        if (session == null) {
            return;
        }
        auth.signOut(session.sessionId());
        sessions.signOut(session.sessionId());
        audit.log(AuditService.SIGN_OUT, session.cardNo(), "Signed out");
        Context.clear();
    }

    /** Throws SESSION_EXPIRED when the customer has been idle too long. */
    public void requireAlive(CustomerSession session) {
        if (session == null) {
            throw new AtmException(AtmException.Reason.SESSION_EXPIRED,
                "Your session timed out. Please sign in again");
        }
        sessions.require(session.sessionId());
    }

    public int secondsLeft(CustomerSession session) {
        return session == null ? 0 : sessions.secondsLeft(session.sessionId());
    }

    // ------------------------------------------------------------------ reading

    public BigDecimal balance(CustomerSession session) {
        requireAlive(session);
        return money.balance(session.accountNo());
    }

    public Customer profile(CustomerSession session) {
        requireAlive(session);
        return cards.profile(session.cardNo());
    }

    public Limits limits(CustomerSession session) {
        requireAlive(session);
        return money.limitsOf(session.accountNo());
    }

    public String limitsText(CustomerSession session) {
        return money.describeLimits(session.accountNo());
    }

    public List<Txn> history(CustomerSession session, LocalDate from, LocalDate to,
            TransactionType type, int limit) {
        requireAlive(session);
        return statements.history(session.accountNo(), from, to, type, limit);
    }

    public List<String> miniStatement(CustomerSession session, int lines) {
        requireAlive(session);
        return statements.miniStatement(session.accountNo(), lines);
    }

    public Path exportStatement(CustomerSession session, LocalDate from, LocalDate to) {
        requireAlive(session);
        return statements.export(session.accountNo(), from, to);
    }

    public BigDecimal spentToday(CustomerSession session) {
        requireAlive(session);
        return money.withdrawnToday(session.accountNo());
    }

    // ------------------------------------------------------------------- money

    public MovementResult deposit(CustomerSession session, String amount, String note, String key) {
        requireAlive(session);
        return money.deposit(session.accountNo(), amount, note, key);
    }

    public MovementResult withdraw(CustomerSession session, String amount, String note,
            String key) {
        requireAlive(session);
        return money.withdraw(session.accountNo(), amount, note, key);
    }

    public MovementResult fastCash(CustomerSession session, BigDecimal amount, String key) {
        requireAlive(session);
        return money.fastCash(session.accountNo(), amount, key);
    }

    public MovementResult transfer(CustomerSession session, String toAccount, String amount,
            String note, String key) {
        requireAlive(session);
        return money.transfer(session.accountNo(), toAccount, amount, note, key);
    }

    /**
     * A transfer above the account's OTP threshold. The threshold itself is checked by the
     * money layer, so a screen can only offer this when it is actually needed.
     */
    public MovementResult transfer(CustomerSession session, String toAccount, String amount,
            String note, String key, String otpCode) {
        requireAlive(session);
        return money.transfer(session.accountNo(), toAccount, amount, note, key, otpCode);
    }

    public boolean transferNeedsOtp(CustomerSession session, String amount) {
        requireAlive(session);
        return limits(session).requiresOtp(Money.of(amount));
    }

    public MovementResult payBill(CustomerSession session, long billerId, String customerRef,
            String amount, String key) {
        requireAlive(session);
        return money.payBill(session.accountNo(), billerId, customerRef, amount, key);
    }

    public MovementResult buyAirtime(CustomerSession session, String network, String phone,
            String amount, String key) {
        requireAlive(session);
        return money.buyAirtime(session.accountNo(), network, phone, amount, key);
    }

    /** The billers a customer can pay; public reference data, no back office involved. */
    public List<Biller> billers() {
        return money.billers();
    }

    public Biller biller(long id) {
        return money.biller(id);
    }

    // ---------------------------------------------------------------- security

    public String requestOtp(CustomerSession session, String purpose) {
        requireAlive(session);
        return otp.issue(session.cardNo(), purpose);
    }

    public void changePin(CustomerSession session, String currentPin, String newPin, String code) {
        requireAlive(session);
        cards.changePin(session.cardNo(), currentPin, newPin, code);
    }

    public void blockCard(CustomerSession session, String code) {
        requireAlive(session);
        cards.blockCard(session.cardNo(), code);
    }

    public void updateProfile(CustomerSession session, String email, String phone, String address,
            String city) {
        requireAlive(session);
        cards.updateProfile(session.cardNo(), email, phone, address, city);
    }

    public void updateContactFor(CustomerSession session, String email, String phone,
            String address, String city) {
        updateProfile(session, email, phone, address, city);
    }

    // ------------------------------------------------------------------ closing

    public void shutdown() {
        sessions.stop();
        Database.shutdown();
    }
}
