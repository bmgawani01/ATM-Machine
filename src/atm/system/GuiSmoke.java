package atm.system;

import atm.core.Bank;
import atm.system.admin.AdminAccounts;
import atm.system.admin.AdminCards;
import atm.system.admin.AdminCash;
import atm.system.admin.AdminCustomers;
import atm.system.admin.AdminDashboard;
import atm.system.admin.AdminReports;
import atm.system.admin.AdminSecurity;
import atm.system.admin.AdminSession;
import atm.system.admin.AdminTransactions;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;

/**
 * Builds every screen against the real database and reports anything that throws.
 *
 * <p>Compilation cannot tell you that a screen queries a column that no longer exists, points
 * at a missing image, or reads a session that was never opened. This does: it signs in, builds
 * each screen in turn, disposes it, and fails the build if any of them blow up on
 * construction.
 *
 * <p>It creates no accounts and moves no money, but signing in is a real sign-in: it updates
 * last-used timestamps and writes audit rows for the demo card and the staff account. Those are
 * left behind on purpose, since an audit log is meant to accumulate.
 *
 * <p>Run it on a machine with a display: {@code java -cp target/atm-system.jar atm.system.GuiSmoke}
 */
public final class GuiSmoke {

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    private GuiSmoke() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("ATM screen smoke test");
        System.out.println("---------------------");

        customerScreens();
        backOffice();

        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("PASS: " + passed + " screens built cleanly.");
            Bank.get().shutdown();
            System.exit(0);
        }
        System.out.println("FAIL: " + failures.size() + " of " + (passed + failures.size())
            + " screens failed:");
        for (String f : failures) {
            System.out.println("  - " + f);
        }
        Bank.get().shutdown();
        System.exit(1);
    }

    private static void customerScreens() throws Exception {
        System.out.println("customer screens (card 1234567890):");
        AppSession.ensureDemo();
        if (!AppSession.isSignedIn()) {
            fail("sign in", "could not sign the demo card in at all");
            return;
        }
        String pin = AppSession.pin();

        build("Login", () -> new Login());
        build("Transactions", () -> new Transactions(pin));
        build("Deposit", () -> new Deposit(pin));
        build("Withdrawl", () -> new Withdrawl(pin));
        build("FastCash", () -> new FastCash(pin));
        build("BalanceEnquiry", () -> new BalanceEnquiry(pin));
        build("MiniStatement", () -> new MiniStatement(pin));
        build("Pin", () -> new Pin(pin));
        build("Transfer", () -> new Transfer(pin));
        build("History", () -> new History(pin));
        build("CardServices", () -> new CardServices(pin));
        build("Airtime", () -> new Airtime(pin));
        build("BillPayment", () -> new BillPayment(pin));
        build("Profile", () -> new Profile(pin));

        // Each page is built on its own staged application, the way the real flow reaches it:
        // a page that is disposed without handing its application on must let go of it.
        int stagedBefore = SignupForm.stagedCount();
        build("Signup page 1", () -> new Signup());
        SignupForm p2 = new SignupForm(null);
        build("Signup page 2", () -> new Signup2(p2.formno));
        SignupForm p3 = new SignupForm(null);
        build("Signup page 3", () -> new Signup3(p3.formno));
        awaitStagedCount(stagedBefore);
        check("abandoned signup applications are released", stagedBefore,
            SignupForm.stagedCount());

        AppSession.signOut();
    }

    private static void backOffice() throws Exception {
        System.out.println("back office screens (staff admin):");

        // Built before signing in: the staff login screen must not depend on a session.
        build("AdminLogin", () -> new atm.system.admin.AdminLogin());

        try {
            AdminSession.open(Bank.get().auth().signInAdmin("admin", "1234"));
        } catch (RuntimeException e) {
            fail("staff sign in", String.valueOf(e.getMessage()));
            return;
        }
        build("AdminDashboard", () -> new AdminDashboard());
        build("AdminCustomers", () -> new AdminCustomers());
        build("AdminAccounts", () -> new AdminAccounts());
        build("AdminTransactions", () -> new AdminTransactions());
        build("AdminCards", () -> new AdminCards());
        build("AdminCash", () -> new AdminCash());
        build("AdminSecurity", () -> new AdminSecurity());
        build("AdminReports", () -> new AdminReports());
        AdminSession.clear();
    }

    private interface Screen {
        void build();
    }

    private static void build(String name, Screen screen) {
        try {
            screen.build();
            passed++;
            System.out.println("  ok    " + name);
        } catch (RuntimeException e) {
            fail(name, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            closeTransientWindows();
        }
    }

    /**
     * Waits for the staged application count to settle.
     *
     * <p>A page releases its staged application from a {@code windowClosed} handler, and window
     * events are delivered on the event dispatch thread, so the count settles a moment after the
     * screen is disposed rather than at the same instant.
     */
    private static void awaitStagedCount(int expected) throws Exception {
        for (int i = 0; i < 100 && SignupForm.stagedCount() != expected; i++) {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
            });
            Thread.sleep(20);
        }
    }

    private static void check(String what, int expected, int actual) {
        if (expected == actual) {
            System.out.println("  ok    " + what);
        } else {
            fail(what, "expected " + expected + " but got " + actual);
        }
    }

    private static void fail(String name, String why) {
        failures.add(name + " -> " + why);
        System.out.println("  FAIL  " + name + " -> " + why);
    }

    private static void closeTransientWindows() {
        try {
            // Disposing on the event dispatch thread lets each screen's own window listener run
            // before the next screen is built, so teardown cannot leak into the next check.
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    if (w instanceof JFrame && w.isVisible()) {
                        w.setVisible(false);
                        w.dispose();
                    }
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Could not close the previous screen", e);
        }
    }
}
