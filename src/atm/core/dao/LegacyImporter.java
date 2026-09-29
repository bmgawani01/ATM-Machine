package atm.core.dao;

import atm.core.Log;
import atm.core.Passwords;
import atm.core.Rows;
import atm.core.model.AccountType;
import atm.core.model.TransactionType;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * One-time import of the data the old version of the app created, so an existing
 * installation does not lose its customers when it moves to the accounts/cards/transactions
 * model. Runs at most once, marked by a {@code LEGACY_IMPORT} audit entry.
 */
public final class LegacyImporter {

    private static final String MARKER = "LEGACY_IMPORT";

    private LegacyImporter() {
    }

    public static void importOnce(Connection c) {
        try {
            if (marked(c)) {
                return;
            }
            int imported = importCustomers(c);
            new AuditDao().log(c, "SYSTEM", null, MARKER,
                "Imported " + imported + " legacy login row(s)", null);
            if (!c.getAutoCommit()) {
                c.commit();
            }
            Log.info("Legacy data import finished: " + imported + " customer(s)");
        } catch (SQLException e) {
            Log.warn("Legacy import skipped: " + e.getMessage());
        }
    }

    private static boolean marked(Connection c) throws SQLException {
        return AccountDao.scalar(c, "SELECT COUNT(*) FROM audit_log WHERE action = '" + MARKER + "'")
            > 0;
    }

    private static int importCustomers(Connection c) throws SQLException {
        CustomerDao customers = new CustomerDao();
        AccountDao accounts = new AccountDao();
        CardDao cards = new CardDao();
        TxnDao txns = new TxnDao();
        LimitDao limits = new LimitDao();
        int count = 0;

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT cardno, pin FROM login");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String cardNo = Rows.str(rs, "cardno");
                String pin = Rows.str(rs, "pin");
                if (cardNo.isEmpty() || pin.isEmpty()) {
                    continue;
                }
                Signup signup = readSignup(c, pin);
                String name = signup.name.isEmpty() ? ("Card holder " + last4(cardNo)) : signup.name;

                customers.insert(c, cardNo, name, signup.fname, signup.dob, signup.gender,
                    signup.email, signup.marital, signup.address, signup.city, signup.religion,
                    signup.category, signup.income, signup.education, signup.occupation, signup.pan,
                    signup.aadhar, "", signup.services, signup.acctype);

                String accountNo = atm.core.Ids.accountNo();
                BigDecimal balance = openingBalance(c, pin);
                accounts.insert(c, accountNo, cardNo,
                    AccountType.fromLabel(signup.acctype), balance);

                String salt = Passwords.newSalt();
                cards.insert(c, cardNo, accountNo, Passwords.hash(pin, salt), salt,
                    LocalDate.now().plusYears(3));

                if (balance.compareTo(BigDecimal.ZERO) > 0) {
                    txns.insert(c, accountNo, TransactionType.DEPOSIT, balance, balance,
                        "Opening balance migrated from the bank table", atm.core.Ids.reference(),
                        null, "ATM");
                }
                limits.upsert(c, accountNo, limits.find(c, accountNo));
                count++;
            }
        }
        return count;
    }

    private static BigDecimal openingBalance(Connection c, String pin) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT mode, amount FROM bank WHERE pin = ?")) {
            ps.setString(1, pin);
            try (ResultSet rs = ps.executeQuery()) {
                BigDecimal total = BigDecimal.ZERO;
                while (rs.next()) {
                    BigDecimal amount = new BigDecimal(Rows.str(rs, "amount").replaceAll("[^0-9.]", ""));
                    if ("Deposit".equalsIgnoreCase(Rows.str(rs, "mode"))) {
                        total = total.add(amount);
                    } else {
                        total = total.subtract(amount);
                    }
                }
                return atm.core.Money.scale(total.max(BigDecimal.ZERO));
            }
        }
    }

    private static Signup readSignup(Connection c, String pin) throws SQLException {
        Signup s = new Signup();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT s.name, s.fname, s.dob, s.gender, s.email, s.marital, s.address, s.city, "
                    + "s2.religion, s2.category, s2.income, s2.education, s2.occupation, s2.pan, "
                    + "s2.aadhar, s3.services, s3.acctype FROM signupthree s3 "
                    + "LEFT JOIN signup s ON s.formno = s3.formno "
                    + "LEFT JOIN signup2 s2 ON s2.formno = s3.formno WHERE s3.pin = ? LIMIT 1")) {
            ps.setString(1, pin);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s.name = Rows.str(rs, "name");
                    s.fname = Rows.str(rs, "fname");
                    s.dob = Rows.str(rs, "dob");
                    s.gender = Rows.str(rs, "gender");
                    s.email = Rows.str(rs, "email");
                    s.marital = Rows.str(rs, "marital");
                    s.address = Rows.str(rs, "address");
                    s.city = Rows.str(rs, "city");
                    s.religion = Rows.str(rs, "religion");
                    s.category = Rows.str(rs, "category");
                    s.income = Rows.str(rs, "income");
                    s.education = Rows.str(rs, "education");
                    s.occupation = Rows.str(rs, "occupation");
                    s.pan = Rows.str(rs, "pan");
                    s.aadhar = Rows.str(rs, "aadhar");
                    s.services = Rows.str(rs, "services");
                    s.acctype = Rows.str(rs, "acctype");
                }
            }
        }
        return s;
    }

    private static String last4(String cardNo) {
        return cardNo.length() <= 4 ? cardNo : cardNo.substring(cardNo.length() - 4);
    }

    private static final class Signup {
        String name = "";
        String fname = "";
        String dob = "";
        String gender = "";
        String email = "";
        String marital = "";
        String address = "";
        String city = "";
        String religion = "";
        String category = "";
        String income = "";
        String education = "";
        String occupation = "";
        String pan = "";
        String aadhar = "";
        String services = "";
        String acctype = "";
    }
}
