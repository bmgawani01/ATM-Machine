package atm.system;

import atm.core.model.AccountType;
import atm.core.service.OnboardingService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The signup application a customer fills in over the three signup screens.
 *
 * <p>Page one creates the form under a form number, pages two and three look it up by that
 * number and add to it, and the account is opened in one go on the last page. Nothing is
 * written to the database until then, so a customer who abandons the form leaves no half
 * finished customer behind.
 */
public final class SignupForm {

    private static final Map<String, SignupForm> STAGED = new ConcurrentHashMap<>();

    public final String formno;

    // page 1
    public String name = "";
    public String fname = "";
    public String dob = "";
    public String gender = "";
    public String email = "";
    public String marital = "";
    public String address = "";
    public String city = "";
    // page 2
    public String religion = "";
    public String category = "";
    public String income = "";
    public String education = "";
    public String occupation = "";
    public String pan = "";
    public String aadhar = "";
    public String seniorCitizen = "No";
    public String existingAccount = "No";
    public String phone = "";
    // page 3
    public String services = "";
    public String acctype = "Saving Account";
    public BigDecimal openingDeposit = BigDecimal.ZERO;

    public SignupForm(String formno) {
        this.formno = formno == null || formno.isBlank() ? nextFormNo() : formno.trim();
        STAGED.put(this.formno, this);
    }

    public static SignupForm of(String formno) {
        return STAGED.get(formno);
    }

    public static void discard(String formno) {
        STAGED.remove(formno);
    }

    public static int stagedCount() {
        return STAGED.size();
    }

    /** The record the bank layer expects, validated on open. */
    public OnboardingService.Application toService() {
        return new OnboardingService.Application(
            name, fname, dob, gender, email, marital, address, city, religion, category,
            income, education, occupation, pan, aadhar, phone, services,
            AccountType.fromLabel(acctype), openingDeposit);
    }

    /** A short reference the customer can quote at the branch. */
    private static String nextFormNo() {
        return "APP" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    }

    @Override
    public String toString() {
        return "SignupForm[" + formno + " " + name + "]";
    }
}
