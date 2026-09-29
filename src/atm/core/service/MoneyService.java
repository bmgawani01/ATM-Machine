package atm.core.service;

import atm.core.AtmException;
import atm.core.Config;
import atm.core.Context;
import atm.core.Database;
import atm.core.Ids;
import atm.core.Log;
import atm.core.Money;
import atm.core.dao.AccountDao;
import atm.core.dao.AtmCashDao;
import atm.core.dao.CardDao;
import atm.core.dao.CustomerDao;
import atm.core.dao.LimitDao;
import atm.core.dao.PaymentDao;
import atm.core.dao.SecurityDao;
import atm.core.dao.TxnDao;
import atm.core.model.Account;
import atm.core.model.AtmCash;
import atm.core.model.Biller;
import atm.core.model.Card;
import atm.core.model.CardStatus;
import atm.core.model.Customer;
import atm.core.model.Limits;
import atm.core.model.MovementResult;
import atm.core.model.TransactionType;
import atm.core.model.Txn;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

/**
 * Everything that moves money: cash deposits, withdrawals, fast cash, fund transfers,
 * bill payments and airtime.
 *
 * <p>Rules for every movement, in one place:
 * <ul>
 *   <li>an idempotency key makes a movement happen at most once;</li>
 *   <li>the balance is changed with a single guarded UPDATE inside one transaction, so two
 *       concurrent withdrawals cannot both pass the balance check;</li>
 *   <li>per-transaction and daily limits are checked before the money moves;</li>
 *   <li>cash payouts also consume notes from the ATM float;</li>
 *   <li>every outcome is written to the audit trail.</li>
 * </ul>
 */
public class MoneyService {

    private final Database db = Database.get();
    private final AccountDao accounts = new AccountDao();
    private final TxnDao txns = new TxnDao();
    private final CardDao cards = new CardDao();
    private final CustomerDao customers = new CustomerDao();
    private final LimitDao limits = new LimitDao();
    private final AtmCashDao atmCash = new AtmCashDao();
    private final SecurityDao security = new SecurityDao();
    private final AuditService audit = new AuditService();

    // -------------------------------------------------------------- balance

    public BigDecimal balance(String accountNo) {
        return db.read(c -> accounts.balance(c, accountNo));
    }

    public BigDecimal balanceOfCard(String cardNo) {
        return db.read(c -> {
            Account a = accounts.findByCardNo(c, cardNo);
            return a == null ? Money.ZERO : a.balance();
        });
    }

    public Limits limitsOf(String accountNo) {
        return db.read(c -> limits.find(c, accountNo));
    }

    // -------------------------------------------------------------- deposit

    public MovementResult deposit(String accountNo, String amountText, String note,
            String idempotencyKey) {
        BigDecimal amount = Money.positive(Money.of(amountText));
        return movement(accountNo, TransactionType.DEPOSIT, amount, note, idempotencyKey, false);
    }

    // ------------------------------------------------------------ withdrawal

    public MovementResult withdraw(String accountNo, String amountText, String note,
            String idempotencyKey) {
        BigDecimal amount = Money.positive(Money.of(amountText));
        Limits l = limitsOf(accountNo);
        if (amount.compareTo(l.perTxn()) > 0) {
            audit.log(AuditService.LIMIT_BLOCKED, accountNo,
                "Withdrawal " + Money.usd(amount) + " above per-transaction limit "
                    + Money.usd(l.perTxn()));
            throw new AtmException(AtmException.Reason.LIMIT_EXCEEDED,
                "Per transaction withdrawal limit is " + Money.usd(l.perTxn()));
        }
        BigDecimal used = db.read(c -> limits.withdrawnToday(c, accountNo));
        if (used.add(amount).compareTo(l.dailyWithdrawal()) > 0) {
            audit.log(AuditService.LIMIT_BLOCKED, accountNo,
                "Daily withdrawal limit reached: used " + Money.usd(used) + " + "
                    + Money.usd(amount));
            throw new AtmException(AtmException.Reason.LIMIT_EXCEEDED,
                "Daily withdrawal limit is " + Money.usd(l.dailyWithdrawal())
                    + " (already used " + Money.usd(used) + ")");
        }
        return movement(accountNo, TransactionType.WITHDRAWAL, amount, note, idempotencyKey, true);
    }

    public MovementResult fastCash(String accountNo, BigDecimal amount, String idempotencyKey) {
        BigDecimal value = Money.positive(Money.scale(amount));
        return movement(accountNo, TransactionType.FAST_CASH, value, "Fast cash", idempotencyKey,
            true);
    }

    // -------------------------------------------------------------- transfer

    /**
     * Moves money to another customer's account. Both accounts are locked in a fixed order to
     * keep two opposite transfers from deadlocking.
     */
    public MovementResult transfer(String fromAccount, String toAccount, String amountText,
            String note, String idempotencyKey) {
        return transfer(fromAccount, toAccount, amountText, note, idempotencyKey, null);
    }

    /**
     * As above, but a transfer above the account's OTP threshold must carry a valid
     * verification code. The threshold check lives here rather than in a screen, so no caller
     * can route around it.
     */
    public MovementResult transfer(String fromAccount, String toAccount, String amountText,
            String note, String idempotencyKey, String otpCode) {
        BigDecimal amount = Money.positive(Money.of(amountText));
        if (fromAccount.equals(toAccount)) {
            throw new AtmException(AtmException.Reason.VALIDATION,
                "Choose an account other than your own");
        }
        Limits l = limitsOf(fromAccount);
        if (amount.compareTo(l.perTxn()) > 0) {
            throw new AtmException(AtmException.Reason.LIMIT_EXCEEDED,
                "Per transaction transfer limit is " + Money.usd(l.perTxn()));
        }
        long transfersToday = db.read(c -> limits.transfersToday(c, fromAccount));
        if (l.dailyTransfers() != null && transfersToday >= l.dailyTransfers()) {
            throw new AtmException(AtmException.Reason.LIMIT_EXCEEDED,
                "Daily transfer limit of " + l.dailyTransfers() + " reached");
        }
        Txn existing = db.read(c -> txns.findByIdempotencyKey(c, idempotencyKey));
        if (existing != null) {
            return duplicate(existing, "This transfer was already processed");
        }
        if (l.requiresOtp(amount)) {
            String fromCard = db.read(c -> accounts.findByNo(c, fromAccount)).cardNo();
            new OtpService().require(fromCard, OtpService.PURPOSE_TRANSFER, otpCode);
        }

        MovementResult result = db.tx(c -> {
            lockBoth(c, fromAccount, toAccount);
            Account from = accounts.findByNo(c, fromAccount);
            Account to = accounts.findByNo(c, toAccount);
            if (from == null || to == null) {
                throw new AtmException(AtmException.Reason.NOT_FOUND,
                    "Destination account not found");
            }
            if (!to.isActive()) {
                throw new AtmException(AtmException.Reason.BLOCKED,
                    "Destination account is " + to.status().toLowerCase());
            }
            BigDecimal newFrom = accounts.debit(c, fromAccount, amount);
            BigDecimal newTo = accounts.credit(c, toAccount, amount);
            Txn out = txns.insert(c, fromAccount, TransactionType.TRANSFER_OUT, amount, newFrom,
                note, Ids.reference(), idempotencyKey, Context.channel());
            Txn in = txns.insert(c, toAccount, TransactionType.TRANSFER_IN, amount, newTo,
                "From " + from.accountNo(), out.reference(), null, Context.channel());
            recordTransfer(c, fromAccount, toAccount, amount, out.reference(), in.reference());
            return new MovementResult(out, newFrom,
                Money.usd(amount) + " transferred to " + Ids.maskAccount(to.cardNo()), false);
        });
        audit.log(AuditService.TRANSFER, fromAccount,
            Money.usd(amount) + " to " + toAccount + " ref " + result.txn().reference());
        return result;
    }

    // --------------------------------------------------------- bill / airtime

    /**
     * The billers a customer may pay. This is public reference data, so the payment screen
     * can read it without going anywhere near the back office.
     */
    public List<Biller> billers() {
        return db.read(c -> new PaymentDao().billers(c));
    }

    public Biller biller(long id) {
        return billers().stream()
            .filter(b -> b.id() == id)
            .findFirst()
            .orElseThrow(() -> new AtmException(AtmException.Reason.VALIDATION,
                "That biller is no longer available"));
    }

    public MovementResult payBill(String accountNo, long billerId, String customerRef,
            String amountText, String idempotencyKey) {
        BigDecimal amount = Money.positive(Money.of(amountText));
        Limits l = limitsOf(accountNo);
        if (amount.compareTo(l.perTxn()) > 0) {
            throw new AtmException(AtmException.Reason.LIMIT_EXCEEDED,
                "Payment above the per transaction limit of " + Money.usd(l.perTxn()));
        }
        long today = db.read(c -> limits.billsToday(c, accountNo));
        if (l.dailyBillPayments() != null && today >= l.dailyBillPayments()) {
            throw new AtmException(AtmException.Reason.LIMIT_EXCEEDED,
                "Daily bill payment limit of " + l.dailyBillPayments() + " reached");
        }
        MovementResult result = movement(accountNo, TransactionType.BILL_PAYMENT, amount,
            "Bill payment " + billerId + " ref " + customerRef, idempotencyKey, false);
        db.read(c -> {
            new atm.core.dao.PaymentDao().recordBillPayment(c, accountNo, billerId, customerRef,
                amount, result.txn().id(), result.txn().reference());
            return null;
        });
        audit.log(AuditService.BILL_PAYMENT, accountNo,
            Money.usd(amount) + " bill " + billerId + " ref " + result.txn().reference());
        return result;
    }

    public MovementResult buyAirtime(String accountNo, String network, String phone,
            String amountText, String idempotencyKey) {
        BigDecimal amount = Money.positive(Money.of(amountText));
        String cleanPhone = phone == null ? "" : phone.replaceAll("\\s", "");
        if (!cleanPhone.matches("\\+?\\d{9,15}")) {
            throw new AtmException(AtmException.Reason.VALIDATION,
                "Enter a valid phone number (9 to 15 digits)");
        }
        if (network == null || network.isBlank()) {
            throw new AtmException(AtmException.Reason.VALIDATION, "Choose a network");
        }
        MovementResult result = movement(accountNo, TransactionType.AIRTIME, amount,
            network + " airtime for " + cleanPhone, idempotencyKey, false);
        db.read(c -> {
            new atm.core.dao.PaymentDao().recordAirtime(c, accountNo, network, cleanPhone, amount,
                result.txn().id(), result.txn().reference());
            return null;
        });
        audit.log(AuditService.AIRTIME, accountNo,
            Money.usd(amount) + " airtime to " + cleanPhone + " (" + network + ")");
        return result;
    }

    // ------------------------------------------------------------- history

    public List<Txn> history(String accountNo, java.time.LocalDate from, java.time.LocalDate to,
            TransactionType type, int limit) {
        return db.read(c -> txns.listByAccount(c, accountNo, from, to, type, limit));
    }

    public List<BigDecimal> dailyBalances(String accountNo, int days) {
        return db.read(c -> {
            List<BigDecimal> out = new java.util.ArrayList<>();
            String sql = "SELECT DATE(created_at) AS d, COALESCE(SUM(IF(type IN "
                + "('WITHDRAWAL','FAST_CASH','TRANSFER_OUT','BILL_PAYMENT','AIRTIME'), "
                + "-amount, amount)), 0) AS movement "
                + "FROM transactions WHERE account_no = ? AND created_at >= ? "
                + "GROUP BY DATE(created_at) ORDER BY d";
            try (var ps = c.prepareStatement(sql)) {
                ps.setString(1, accountNo);
                ps.setTimestamp(2, java.sql.Timestamp.valueOf(
                    java.time.LocalDate.now().minusDays(days).atStartOfDay()));
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(Money.scale(rs.getBigDecimal("movement")));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------ internals

    /**
     * The single place a balance changes. Debits additionally consume ATM notes.
     */
    private MovementResult movement(String accountNo, TransactionType type, BigDecimal amount,
            String note, String idempotencyKey, boolean cashOut) {
        String key = idempotencyKey == null || idempotencyKey.isBlank()
            ? Ids.idempotencyKey() : idempotencyKey;

        Txn existing = db.read(c -> txns.findByIdempotencyKey(c, key));
        if (existing != null) {
            audit.log(AuditService.DUPLICATE_BLOCKED, accountNo,
                "Ignored repeated " + type.label() + " of " + Money.usd(amount)
                    + " (reference " + existing.reference() + ")");
            Log.info("Duplicate " + type + " ignored for account " + Ids.maskAccount(accountNo));
            return duplicate(existing, "This transaction was already processed ("
                + existing.reference() + ")");
        }

        try {
            MovementResult result = db.tx(c -> {
                Account account = accounts.lock(c, accountNo);
                if (account.status().equals("FROZEN")) {
                    throw new AtmException(AtmException.Reason.BLOCKED,
                        "Account " + accountNo + " is frozen");
                }
                if (type.isDebit() && cashOut && !takeNotes(c, amount)) {
                    throw new AtmException(AtmException.Reason.UNAVAILABLE,
                        "The machine does not hold enough cash for this amount");
                }
                BigDecimal balanceAfter = type.isDebit()
                    ? accounts.debit(c, accountNo, amount)
                    : accounts.credit(c, accountNo, amount);
                Txn txn = txns.insert(c, accountNo, type, amount, balanceAfter, note,
                    Ids.reference(), key, Context.channel());
                return new MovementResult(txn, balanceAfter,
                    type.label() + " of " + Money.usd(amount) + " successful", false);
            });
            audit.log(auditAction(type), accountNo,
                Money.usd(amount) + " " + type.label() + " ref " + result.txn().reference()
                    + ", balance " + Money.usd(result.balance()));
            return result;
        } catch (AtmException e) {
            if (e.reason() == AtmException.Reason.INSUFFICIENT_FUNDS) {
                audit.log(auditAction(type), accountNo, "Rejected: " + e.userMessage());
            }
            throw e;
        }
    }

    /** Consumes notes from the float; rolls back with the rest if the balance update fails. */
    private boolean takeNotes(Connection c, BigDecimal amount) throws java.sql.SQLException {
        int[] denoms = atmCash.denominations(c);
        java.util.Arrays.sort(denoms);
        int[] counts = new int[denoms.length];
        long remaining = Money.scale(amount).movePointRight(2).longValue();
        for (int i = denoms.length - 1; i >= 0 && remaining > 0; i--) {
            long value = denoms[i] * 100L;
            if (value <= 0) {
                continue;
            }
            long count = remaining / value;
            if (count > 0) {
                counts[i] = (int) count;
                remaining -= count * value;
            }
        }
        if (remaining > 0) {
            return false;
        }
        return atmCash.dispense(c, denoms, counts);
    }

    private void recordTransfer(Connection c, String from, String to, BigDecimal amount,
            String fromRef, String toRef) throws java.sql.SQLException {
        try (var ps = c.prepareStatement(
                "INSERT INTO transfers (from_account, to_account, amount, from_ref, to_ref) "
                    + "VALUES (?,?,?,?,?)")) {
            ps.setString(1, from);
            ps.setString(2, to);
            ps.setBigDecimal(3, Money.scale(amount));
            ps.setString(4, fromRef);
            ps.setString(5, toRef);
            ps.executeUpdate();
        }
    }

    /** Locks two accounts in a stable order so opposite transfers cannot deadlock. */
    private void lockBoth(Connection c, String a, String b) throws java.sql.SQLException {
        String first = a.compareTo(b) <= 0 ? a : b;
        String second = a.compareTo(b) <= 0 ? b : a;
        accounts.lock(c, first);
        accounts.lock(c, second);
    }

    private MovementResult duplicate(Txn existing, String message) {
        return new MovementResult(existing, existing.balanceAfter(), message, true);
    }

    private static String auditAction(TransactionType type) {
        switch (type) {
            case DEPOSIT:
                return AuditService.DEPOSIT;
            case WITHDRAWAL:
                return AuditService.WITHDRAWAL;
            case FAST_CASH:
                return AuditService.FAST_CASH;
            case BILL_PAYMENT:
                return AuditService.BILL_PAYMENT;
            case AIRTIME:
                return AuditService.AIRTIME;
            case TRANSFER_OUT:
            case TRANSFER_IN:
                return AuditService.TRANSFER;
            default:
                return type.name();
        }
    }

    /** Convenience for screens: card number straight to balance. */
    public BigDecimal balanceFor(String cardNo) {
        return balanceOfCard(cardNo);
    }

    /** Used by the ATM cash screen. */
    public List<AtmCash> atmCashLevels() {
        return db.read(atmCash::levels);
    }

    public Card card(String cardNo) {
        return db.read(c -> cards.find(c, cardNo));
    }

    public CardStatus cardStatus(String cardNo) {
        Card card = card(cardNo);
        return card == null ? CardStatus.ACTIVE : card.status();
    }

    public void invalidateSessions(String cardNo) {
        db.read(c -> {
            security.revokeAllForCard(c, cardNo, "SECURITY_CHANGE");
            return null;
        });
    }

    public String describeLimits(String accountNo) {
        Limits l = limitsOf(accountNo);
        return "Per transaction " + Money.usd(l.perTxn())
            + " | Daily cash " + Money.usd(l.dailyWithdrawal())
            + " | Daily transfers " + l.dailyTransfers()
            + " | OTP above " + Money.usd(l.requireOtpAbove());
    }

    /** The amount the customer has already withdrawn today, for the mini statement. */
    public BigDecimal withdrawnToday(String accountNo) {
        return db.read(c -> limits.withdrawnToday(c, accountNo));
    }

    public Customer customer(String cardNo) {
        return db.read(c -> customers.findByCardNo(c, cardNo));
    }

    public int defaultPoolHint() {
        return Config.getInt(Config.Setting.DB_POOL_SIZE, 5);
    }
}
