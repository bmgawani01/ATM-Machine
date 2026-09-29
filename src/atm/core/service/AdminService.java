package atm.core.service;

import atm.core.AtmException;
import atm.core.Database;
import atm.core.dao.AccountDao;
import atm.core.dao.AdminUserDao;
import atm.core.dao.AtmCashDao;
import atm.core.dao.AuditDao;
import atm.core.dao.CardDao;
import atm.core.dao.CustomerDao;
import atm.core.dao.LimitDao;
import atm.core.dao.PaymentDao;
import atm.core.dao.ReportDao;
import atm.core.dao.TxnDao;
import atm.core.model.Account;
import atm.core.model.AccountSummary;
import atm.core.model.AccountType;
import atm.core.model.AtmCash;
import atm.core.model.AuditEntry;
import atm.core.model.Biller;
import atm.core.model.Card;
import atm.core.model.CardStatus;
import atm.core.model.Customer;
import atm.core.model.DashboardStats;
import atm.core.model.FailedLogin;
import atm.core.model.Limits;
import atm.core.model.Txn;
import atm.core.model.TransactionType;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The back office: dashboard, customer / account / card management, transaction monitoring,
 * failed-login monitoring, machine cash levels and the report tables.
 *
 * <p>Every change is written to the audit trail with the staff member who made it, and the
 * reads are plain queries, so a teller screen never has to write SQL.
 */
public class AdminService {

    private final Database db = Database.get();
    private final ReportDao reports = new ReportDao();
    private final CustomerDao customers = new CustomerDao();
    private final AccountDao accounts = new AccountDao();
    private final CardDao cards = new CardDao();
    private final TxnDao txns = new TxnDao();
    private final AuditDao auditDao = new AuditDao();
    private final AtmCashDao atmCash = new AtmCashDao();
    private final PaymentDao payments = new PaymentDao();
    private final AdminUserDao admins = new AdminUserDao();
    private final LimitDao limits = new LimitDao();
    private final AuditService audit = new AuditService();
    private final CardService cardService = new CardService();
    private final StatementService statements = new StatementService();

    // ---------------------------------------------------------------- dashboard

    public DashboardStats dashboard() {
        return db.read(reports::stats);
    }

    /** True when the app can still reach the database; shown on the dashboard. */
    public boolean healthy() {
        return db.healthy();
    }

    public int poolActive() {
        return db.poolActive();
    }

    public int poolIdle() {
        return db.poolIdle();
    }

    // ---------------------------------------------------------------- customers

    public List<Customer> searchCustomers(String search, String status, int limit) {
        return db.read(c -> customers.list(c, search, status, limit));
    }

    public Customer customer(String cardNo) {
        return db.read(c -> customers.findByCardNo(c, cardNo));
    }

    public void setCustomerStatus(String cardNo, String status, String actor) {
        db.read(c -> {
            customers.setStatus(c, cardNo, status);
            return null;
        });
        audit.log(AuditService.ADMIN_ACTION, cardNo, "Customer set to " + status, actor);
    }

    public long customerCount(String status) {
        return db.read(c -> status == null || status.isBlank()
            ? customers.count(c) : customers.countByStatus(c, status));
    }

    // ----------------------------------------------------------------- accounts

    public List<Account> listAccounts(String search, int limit) {
        return db.read(c -> accounts.list(c, search, limit));
    }

    public Account account(String accountNo) {
        return db.read(c -> accounts.findByNo(c, accountNo));
    }

    public BigDecimal totalBalances() {
        return db.read(accounts::totalBalance);
    }

    public void setAccountStatus(String accountNo, String status, String actor) {
        cardService.setAccountStatus(accountNo, status, actor);
    }

    public void setAccountType(String accountNo, AccountType type, String actor) {
        cardService.setAccountType(accountNo, type, actor);
    }

    public void setLimits(String accountNo, BigDecimal perTxn, BigDecimal dailyWithdrawal,
            Integer dailyTransfers, Integer dailyBills, BigDecimal otpAbove, String actor) {
        cardService.updateLimits(accountNo, perTxn, dailyWithdrawal, dailyTransfers, dailyBills,
            otpAbove, actor);
    }

    public Limits limits(String accountNo) {
        return db.read(c -> limits.find(c, accountNo));
    }

    /** Customer + account + card in one row, for the admin list screens. */
    public AccountSummary summary(String cardNo) {
        return db.read(c -> new AccountSummary(
            customers.findByCardNo(c, cardNo),
            accounts.findByCardNo(c, cardNo),
            cards.find(c, cardNo)));
    }

    // -------------------------------------------------------------------- cards

    public List<Card> listCards(String search, int limit) {
        return db.read(c -> cards.list(c, search, limit));
    }

    public Card card(String cardNo) {
        return db.read(c -> cards.find(c, cardNo));
    }

    public long cardCount(CardStatus status) {
        return db.read(c -> cards.count(c, status));
    }

    public void setCardStatus(String cardNo, CardStatus status, String actor) {
        cardService.setCardStatus(cardNo, status, actor);
    }

    public void extendCard(String cardNo, int months, String actor) {
        cardService.extendCard(cardNo, months, actor);
    }

    public void resetCardLock(String cardNo, String actor) {
        db.read(c -> {
            cards.unblock(c, cardNo);
            cards.resetFailedAttempts(c, cardNo);
            return null;
        });
        audit.log(AuditService.CARD_UNBLOCKED, cardNo, "Lock cleared by staff", actor);
    }

    /** Changes a customer's PIN from the back office, for a card reported as broken. */
    public void setCardPin(String cardNo, String newPin, String actor) {
        String pin = newPin == null ? "" : newPin.trim();
        if (!pin.matches("\\d{4,6}")) {
            throw new AtmException(AtmException.Reason.VALIDATION,
                "The new PIN must be 4 to 6 digits");
        }
        db.read(c -> {
            cards.changePin(c, cardNo, pin);
            return null;
        });
        audit.log(AuditService.PIN_CHANGE, cardNo, "PIN reset by staff", actor);
    }

    // ------------------------------------------------------------- transactions

    public List<Txn> recentTransactions(int limit) {
        return db.read(c -> txns.recent(c, limit));
    }

    public List<Txn> accountTransactions(String accountNo, LocalDate from, LocalDate to,
            TransactionType type, int limit) {
        return statements.history(accountNo, from, to, type, limit);
    }

    public long transactionsToday() {
        return db.read(txns::countToday);
    }

    public BigDecimal volumeToday() {
        return db.read(c -> txns.sumByTypeToday(c, null));
    }

    // ------------------------------------------------------------------- audit

    public List<AuditEntry> recentAudit(String action, String cardNo, int limit) {
        return db.read(c -> auditDao.recent(c, action, cardNo, limit));
    }

    public List<FailedLogin> recentFailedLogins(int limit) {
        return db.read(c -> auditDao.failedLogins(c, limit));
    }

    public long failedLoginsSince(LocalDateTime since, String cardNo) {
        return db.read(c -> auditDao.failedSince(c, since, cardNo));
    }

    public long auditCount(String action, LocalDateTime since) {
        return db.read(c -> auditDao.countByAction(c, action, since));
    }

    // ---------------------------------------------------------------- machine cash

    public List<AtmCash> atmCashLevels() {
        return db.read(atmCash::levels);
    }

    public BigDecimal atmCashTotal() {
        return db.read(atmCash::total);
    }

    public void restockCash(int denom, int notes, String actor) {
        if (notes <= 0) {
            throw new AtmException(AtmException.Reason.VALIDATION,
                "Enter how many notes were added");
        }
        db.read(c -> {
            atmCash.restock(c, denom, notes);
            return null;
        });
        audit.log(AuditService.ADMIN_ACTION, null,
            "Restocked " + notes + " x " + denom + " note(s)", actor);
    }

    public void setCashLevel(int denom, int notes, String actor) {
        if (notes < 0) {
            throw new AtmException(AtmException.Reason.VALIDATION,
                "The number of notes cannot be negative");
        }
        db.read(c -> {
            atmCash.setLevel(c, denom, notes);
            return null;
        });
        audit.log(AuditService.ADMIN_ACTION, null,
            "Set " + denom + " notes to " + notes, actor);
    }

    // ------------------------------------------------------------------- payments

    /**
     * Re-reads a staff account. The back office calls this every time a screen opens, so a
     * staff member who has been disabled or locked mid-session stops being able to work.
     */
    public AdminUserDao.Admin findAdmin(String username) {
        return username == null || username.isBlank() ? null
            : db.read(c -> admins.find(c, username.trim()));
    }

    public List<Biller> billers() {
        return db.read(payments::billers);
    }

    public List<String[]> recentBillPayments(int limit) {
        return db.read(c -> payments.recentBillPayments(c, limit));
    }

    public List<String[]> recentAirtime(int limit) {
        return db.read(c -> payments.recentAirtime(c, limit));
    }

    // -------------------------------------------------------------------- reports

    public List<String[]> dailyReport(LocalDate from, LocalDate to) {
        return db.read(c -> reports.dailyReport(c, from, to));
    }

    public List<String[]> typeReport(LocalDate from, LocalDate to) {
        return db.read(c -> reports.typeReport(c, from, to));
    }

    public List<String[]> topCustomers(LocalDate from, LocalDate to, int limit) {
        return db.read(c -> reports.topCustomers(c, from, to, limit));
    }

    public Path dailyCsv(LocalDate from, LocalDate to) {
        return statements.exportReport("daily-transactions",
            new String[] {"Date", "Transactions", "Deposits", "Withdrawals", "Transfers",
                "Net"},
            dailyReport(from, to));
    }

    public Path typeCsv(LocalDate from, LocalDate to) {
        return statements.exportReport("transactions-by-type",
            new String[] {"Type", "Count", "Total"}, typeReport(from, to));
    }

    public Path topCustomersCsv(LocalDate from, LocalDate to, int limit) {
        return statements.exportReport("top-customers",
            new String[] {"Customer", "Card", "Transactions", "Total"}, topCustomers(from, to, limit));
    }

    // ------------------------------------------------------------------- staff

    public void setStaffStatus(String username, String status, String actor) {
        db.read(c -> {
            admins.setStatus(c, username, status);
            return null;
        });
        audit.log(AuditService.ADMIN_ACTION, null,
            "Staff account " + username + " set to " + status, actor);
    }

    public void setStaffPin(String username, String newPin, String actor) {
        String pin = newPin == null ? "" : newPin.trim();
        if (!pin.matches("\\d{4,6}")) {
            throw new AtmException(AtmException.Reason.VALIDATION,
                "The new PIN must be 4 to 6 digits");
        }
        db.read(c -> {
            admins.changePin(c, username, pin);
            return null;
        });
        audit.log(AuditService.PIN_CHANGE, null, "Staff PIN changed for " + username, actor);
    }
}
