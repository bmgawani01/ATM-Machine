package atm.core.service;

import atm.core.AtmException;
import atm.core.Database;
import atm.core.Ids;
import atm.core.Log;
import atm.core.dao.AccountDao;
import atm.core.dao.CardDao;
import atm.core.dao.CustomerDao;
import atm.core.dao.LimitDao;
import atm.core.model.Account;
import atm.core.model.AccountType;
import atm.core.model.Card;
import atm.core.model.CardStatus;
import atm.core.model.Customer;
import atm.core.model.Limits;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Card blocking, PIN change and profile updates. */
public class CardService {

    private final Database db = Database.get();
    private final CardDao cards = new CardDao();
    private final CustomerDao customers = new CustomerDao();
    private final AccountDao accounts = new AccountDao();
    private final LimitDao limits = new LimitDao();
    private final AuditService audit = new AuditService();
    private final AuthService auth = new AuthService();

    /** Customer-initiated block. Reversible, and requires the OTP first. */
    public void blockCard(String cardNo, String otpCode) {
        OtpService otp = new OtpService();
        otp.require(cardNo, OtpService.PURPOSE_CARD_BLOCK, otpCode);
        db.read(c -> {
            cards.setStatus(c, cardNo, CardStatus.BLOCKED);
            security_revoke(c, cardNo);
            return null;
        });
        auth.signOutAllForCard(cardNo, "CARD_BLOCKED");
        audit.log(AuditService.CARD_BLOCKED, cardNo, "Card blocked by the card holder");
        Log.info("Card " + Ids.maskCard(cardNo) + " blocked at the customer's request");
    }

    public void unblockCard(String cardNo, String actor) {
        db.read(c -> {
            cards.unblock(c, cardNo);
            return null;
        });
        audit.log(AuditService.CARD_UNBLOCKED, cardNo, "Card unblocked", actor);
    }

    /**
     * PIN change. The new PIN must be 4 digits and different from the current one, and the
     * OTP for this card must have been verified first. Every session for the card is then
     * revoked so an attacker with the old PIN loses access immediately.
     */
    public void changePin(String cardNo, String currentPin, String newPin, String otpCode) {
        String pin = newPin == null ? "" : newPin.trim();
        if (!pin.matches("\\d{4,6}")) {
            throw new AtmException(AtmException.Reason.VALIDATION,
                "The new PIN must be 4 to 6 digits");
        }
        if (pin.equals(currentPin == null ? "" : currentPin.trim())) {
            throw new AtmException(AtmException.Reason.VALIDATION,
                "The new PIN must be different from the current one");
        }
        if (!db.read(c -> cards.pinMatches(c, cardNo, currentPin))) {
            throw new AtmException(AtmException.Reason.AUTHENTICATION,
                "The current PIN is not correct");
        }
        new OtpService().require(cardNo, OtpService.PURPOSE_PIN_CHANGE, otpCode);
        db.read(c -> {
            cards.changePin(c, cardNo, pin);
            security_revoke(c, cardNo);
            return null;
        });
        auth.signOutAllForCard(cardNo, "PIN_CHANGED");
        audit.log(AuditService.PIN_CHANGE, cardNo, "PIN changed, all sessions revoked");
        Log.info("PIN changed for card " + Ids.maskCard(cardNo));
    }

    /** Update the profile fields a customer is allowed to change. */
    public void updateProfile(String cardNo, String email, String phone, String address, String city) {
        if (email != null && !email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$")) {
            throw new AtmException(AtmException.Reason.VALIDATION, "Enter a valid email address");
        }
        if (phone != null && !phone.isBlank() && !phone.replaceAll("\\s", "").matches("\\+?\\d{9,15}")) {
            throw new AtmException(AtmException.Reason.VALIDATION, "Enter a valid phone number");
        }
        db.read(c -> {
            customers.updateContact(c, cardNo, email, phone, address, city);
            return null;
        });
        audit.log(AuditService.ADMIN_ACTION, cardNo, "Profile updated by the customer");
    }

    public Customer profile(String cardNo) {
        return db.read(c -> customers.findByCardNo(c, cardNo));
    }

    public Card card(String cardNo) {
        return db.read(c -> cards.find(c, cardNo));
    }

    public Account account(String cardNo) {
        return db.read(c -> accounts.findByCardNo(c, cardNo));
    }

    public Limits limits(String accountNo) {
        return db.read(c -> limits.find(c, accountNo));
    }

    public void updateLimits(String accountNo, BigDecimal perTxn, BigDecimal dailyWithdrawal,
            Integer dailyTransfers, Integer dailyBills, BigDecimal otpAbove) {
        updateLimits(accountNo, perTxn, dailyWithdrawal, dailyTransfers, dailyBills, otpAbove,
            null);
    }

    /**
     * As above, naming the staff member in the audit entry, so the back office can answer
     * "who raised this customer's limits".
     */
    public void updateLimits(String accountNo, BigDecimal perTxn, BigDecimal dailyWithdrawal,
            Integer dailyTransfers, Integer dailyBills, BigDecimal otpAbove, String actor) {
        Limits l = new Limits(perTxn, dailyWithdrawal, dailyTransfers, dailyBills, otpAbove);
        db.read(c -> {
            limits.upsert(c, accountNo, l);
            return null;
        });
        audit.log(AuditService.ADMIN_ACTION, accountNo,
            "Limits updated: " + l.perTxn() + " per txn, " + l.dailyWithdrawal() + " daily", actor);
    }

    public void setAccountStatus(String accountNo, String status, String actor) {
        db.read(c -> {
            accounts.setStatus(c, accountNo, status);
            return null;
        });
        audit.log(AuditService.ADMIN_ACTION, accountNo, "Account set to " + status, actor);
    }

    public void setAccountType(String accountNo, AccountType type, String actor) {
        db.read(c -> {
            accounts.setType(c, accountNo, type);
            return null;
        });
        audit.log(AuditService.ADMIN_ACTION, accountNo, "Account type set to " + type.code(), actor);
    }

    public void setCardStatus(String cardNo, CardStatus status, String actor) {
        db.read(c -> {
            if (status == CardStatus.ACTIVE) {
                cards.unblock(c, cardNo);
            } else {
                cards.setStatus(c, cardNo, status);
            }
            return null;
        });
        audit.log(status == CardStatus.ACTIVE ? AuditService.CARD_UNBLOCKED : AuditService.CARD_BLOCKED,
            cardNo, "Card set to " + status + " by staff", actor);
    }

    public void extendCard(String cardNo, int months, String actor) {
        LocalDate newExpiry = LocalDate.now().plusMonths(months);
        db.read(c -> {
            cards.setExpiry(c, cardNo, newExpiry);
            return null;
        });
        audit.log(AuditService.ADMIN_ACTION, cardNo, "Card expiry extended to " + newExpiry, actor);
    }

    private void security_revoke(java.sql.Connection c, String cardNo)
            throws java.sql.SQLException {
        new atm.core.dao.SecurityDao().revokeAllForCard(c, cardNo, "SECURITY_CHANGE");
    }
}
