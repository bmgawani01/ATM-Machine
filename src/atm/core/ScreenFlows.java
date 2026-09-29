package atm.core;

import atm.core.model.AccountType;
import atm.core.model.AuthSession;
import atm.core.model.Limits;
import atm.core.model.MovementResult;
import atm.core.service.OnboardingService;
import atm.core.service.OtpService;
import java.math.BigDecimal;

/**
 * The rules the screens depend on, checked without a screen.
 *
 * <p>{@link atm.system.GuiSmoke} proves every screen builds. This proves the two things it
 * cannot check, because they need a button pressed: that the three-page signup actually opens
 * a usable account, and that a large transfer really is refused without a verification code
 * and really goes through with one.
 *
 * <p>Run it against a throwaway database so it cannot touch real data:
 * <pre>
 * java -Ddb.name=atm_flow_test -cp target/atm-system.jar atm.core.ScreenFlows
 * </pre>
 */
public final class ScreenFlows {

    private static int passed;
    private static int failed;

    private ScreenFlows() {
    }

    public static void main(String[] args) {
        Bank bank = Bank.get();
        System.out.println("Screen contract checks against " + Config.databaseName());
        System.out.println("--------------------------------------------");

        String suffix = Long.toString(System.currentTimeMillis(), 36);

        OnboardingService.NewAccount alice = openAccount("Alice Tester", "a" + suffix, "12000");
        OnboardingService.NewAccount bob = openAccount("Bob Tester", "b" + suffix, "100");

        signupWorks(alice, "a" + suffix);
        transferWithoutCodeIsRefused(bank, alice, bob);
        transferWithCodeGoesThrough(bank, alice, bob);
        pinChangeRevokesTheSession(bank, alice);

        System.out.println();
        System.out.println("RESULT: " + (failed == 0 ? "OK" : "FAILED")
            + " (" + passed + " passed, " + failed + " failed)");
        bank.shutdown();
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ signup

    private static void signupWorks(OnboardingService.NewAccount account, String suffix) {
        AuthSession session = Bank.get().auth().signIn(account.cardNo(), account.pin());
        ok("the new account can sign in", session != null);
        ok("the card is linked to the account", account.accountNo().equals(
            Bank.get().auth().signIn(account.cardNo(), account.pin()).account().accountNo()));

        check("the opening deposit is the balance", "12000.00",
            Bank.get().money().balance(account.accountNo()).toPlainString());
        count("the opening deposit was recorded as one movement", 1,
            Bank.get().statements().history(account.accountNo(), java.time.LocalDate.now()
                .minusDays(1), java.time.LocalDate.now().plusDays(1), null, 20).size());
        check("the account type was stored", AccountType.SAVING.code(), account.accountType());
        count("the new customer can be found in the directory", 1,
            count(Bank.get().admin().searchCustomers(suffix, null, 50)));

        Limits limits = Bank.get().money().limitsOf(account.accountNo());
        ok("the new account has limits", limits != null && limits.perTxn().signum() > 0);

        boolean wrongPinRefused = false;
        try {
            Bank.get().auth().signIn(account.cardNo(), "9999");
        } catch (AtmException e) {
            wrongPinRefused = e.reason() == AtmException.Reason.AUTHENTICATION;
        }
        ok("the generated PIN is the only one that works", wrongPinRefused);
    }

    // ---------------------------------------------------------------- transfer

    private static void transferWithoutCodeIsRefused(Bank bank, OnboardingService.NewAccount from,
            OnboardingService.NewAccount to) {
        Bank.CustomerSession session = bank.signIn(from.cardNo(), from.pin());
        String amount = "6000"; // above the 5000 default OTP threshold

        ok("a large transfer needs a code", bank.transferNeedsOtp(session, amount));
        ok("a small transfer does not", !bank.transferNeedsOtp(session, "100"));

        AtmException refused = null;
        try {
            bank.transfer(session, to.accountNo(), amount, "no code", null, null);
        } catch (AtmException e) {
            refused = e;
        }
        ok("a large transfer with no code is refused",
            refused != null && refused.reason() == AtmException.Reason.OTP_INVALID);
        check("no money left the account", "12000.00",
            bank.balance(session).toPlainString());
        check("the destination received nothing", "100.00",
            bank.money().balance(to.accountNo()).toPlainString());
    }

    private static void transferWithCodeGoesThrough(Bank bank, OnboardingService.NewAccount from,
            OnboardingService.NewAccount to) {
        Bank.CustomerSession session = bank.signIn(from.cardNo(), from.pin());
        String amount = "6000";
        String code = bank.requestOtp(session, OtpService.PURPOSE_TRANSFER);

        MovementResult result = bank.transfer(session, to.accountNo(), amount, "with a code",
            null, code);
        check("the money left the account", "6000.00", result.balance().toPlainString());
        check("the destination received it", "6100.00",
            bank.money().balance(to.accountNo()).toPlainString());

        boolean reused = false;
        try {
            bank.transfer(session, to.accountNo(), amount, "same code again", null, code);
        } catch (AtmException e) {
            reused = e.reason() == AtmException.Reason.OTP_INVALID;
        }
        ok("the code cannot be used twice", reused);
        check("the balance did not move again", "6000.00",
            bank.balance(session).toPlainString());
    }

    // ------------------------------------------------------------------- pin

    private static void pinChangeRevokesTheSession(Bank bank, OnboardingService.NewAccount a) {
        Bank.CustomerSession session = bank.signIn(a.cardNo(), a.pin());
        String code = bank.requestOtp(session, OtpService.PURPOSE_PIN_CHANGE);
        bank.changePin(session, a.pin(), "4321", code);

        boolean oldSessionDead = false;
        try {
            bank.requireAlive(session);
        } catch (AtmException e) {
            oldSessionDead = e.reason() == AtmException.Reason.SESSION_EXPIRED;
        }
        ok("changing the PIN kills the session it was changed from", oldSessionDead);

        bank.signIn(a.cardNo(), "4321");
        ok("the new PIN signs in", true);
    }

    // ------------------------------------------------------------------ helpers

    private static OnboardingService.NewAccount openAccount(String name, String suffix,
            String opening) {
        // The suffix goes in the email so the directory search below has something to match.
        OnboardingService.Application app = new OnboardingService.Application(
            name,
            "Tester Father",
            "01/01/1990",
            "Female",
            name.toLowerCase().replace(' ', '.') + "." + suffix + "@example.com",
            "Unmarried",
            "1 Test Street",
            "Testville",
            "Hindu",
            "General",
            "1,00,000 - 5,00,000",
            "Graduate",
            "Salaried",
            "ABCDE1234F",
            "100020000" + Math.abs(suffix.hashCode() % 1000),
            "077700900" + Math.abs(suffix.hashCode() % 1000),
            "ATM Card",
            AccountType.SAVING,
            new BigDecimal(opening));
        return Bank.get().onboarding().openAccount(app);
    }

    private static long count(java.util.List<atm.core.model.Customer> customers) {
        return customers.stream().filter(c -> c.status().equals("ACTIVE")).count();
    }

    private static void count(String what, long expected, long actual) {
        if (expected == actual) {
            passed++;
            System.out.println("  PASS  " + what + " (" + actual + ")");
        } else {
            failed++;
            System.out.println("  FAIL  " + what + ": expected " + expected + " but got "
                + actual);
        }
    }

    private static void ok(String what, boolean holds) {
        if (holds) {
            passed++;
            System.out.println("  PASS  " + what);
        } else {
            failed++;
            System.out.println("  FAIL  " + what);
        }
    }

    private static void check(String what, String expected, String actual) {
        if (expected.equals(actual)) {
            passed++;
            System.out.println("  PASS  " + what + " (" + actual + ")");
        } else {
            failed++;
            System.out.println("  FAIL  " + what + ": expected " + expected + " but got "
                + actual);
        }
    }
}
