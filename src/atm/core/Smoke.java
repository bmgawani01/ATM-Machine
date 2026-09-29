package atm.core;

import atm.core.model.AccountType;
import atm.core.model.AuthSession;
import atm.core.model.CardStatus;
import atm.core.model.DashboardStats;
import atm.core.model.Limits;
import atm.core.model.MovementResult;
import atm.core.model.Txn;
import atm.core.service.AdminService;
import atm.core.service.AuditService;
import atm.core.service.AuthService;
import atm.core.service.CardService;
import atm.core.service.MoneyService;
import atm.core.service.OnboardingService;
import atm.core.service.OtpService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * End-to-end check of the {@code atm.core} layer against a real database: builds the pool,
 * creates the schema, opens two accounts, then drives the money and security rules the way
 * the screens will. Run it with:
 *
 * <pre>java -cp atm-system.jar atm.core.Smoke</pre>
 *
 * It prints one PASS/FAIL line per rule and exits non-zero if anything failed. It writes a
 * few tiny transactions to whatever database it is pointed at, so point it at a test
 * database, not the branch one.
 */
public final class Smoke {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        System.out.println("atm.core smoke test");
        Database.shutdown();
        try {
            Database db = Database.get();
            ok("connection pool is healthy", db.healthy());
            counts(db);

            OnboardingService onboarding = new OnboardingService();
            OnboardingService.NewAccount a = onboard(onboarding, "Smoke One", "0771000001");
            OnboardingService.NewAccount b = onboard(onboarding, "Smoke Two", "0771000002");
            System.out.println("  account A " + a.accountNo() + "  card " + Ids.maskCard(a.cardNo()));
            System.out.println("  account B " + b.accountNo() + "  card " + Ids.maskCard(b.cardNo()));

            AuthService auth = new AuthService();
            MoneyService money = new MoneyService();
            CardService cards = new CardService();

            AuthSession sessionA = auth.signIn(a.cardNo(), a.pin(), "ATM");
            AuthSession sessionB = auth.signIn(b.cardNo(), b.pin(), "ATM");
            ok("sign-in works for both customers", sessionA.sessionId() != null
                && sessionB.sessionId() != null);
            ok("session is alive right after sign-in", auth.isSessionAlive(sessionA.sessionId()));

            money("opening deposit was recorded", "1000.00", money.balance(a.accountNo()));
            money("deposit of 500 credited", "1500.00",
                money.deposit(a.accountNo(), "500", "smoke deposit", null).balance());
            money("withdrawal of 200 debited", "1300.00",
                money.withdraw(a.accountNo(), "200", "smoke withdrawal", null).balance());

            MovementResult transfer = money.transfer(a.accountNo(), b.accountNo(), "150",
                "smoke transfer", null);
            money("transfer of 150 left account A", "1150.00", transfer.balance());
            money("transfer of 150 reached account B", "1150.00", money.balance(b.accountNo()));

            String key = Ids.idempotencyKey();
            MovementResult first = money.withdraw(a.accountNo(), "40", "once", key);
            MovementResult second = money.withdraw(a.accountNo(), "40", "same key again", key);
            ok("a repeated idempotency key is charged only once",
                !first.duplicate() && second.duplicate() && second.message().contains("already")
                    && "1110.00".equals(money.balance(a.accountNo()).toPlainString()));

            cards.updateLimits(a.accountNo(), new BigDecimal("1000"), new BigDecimal("5000"),
                10, 10, new BigDecimal("500"));
            Limits limits = money.limitsOf(a.accountNo());
            BigDecimal tooBig = limits.perTxn().add(BigDecimal.ONE);
            expect("amount above the per-transaction limit is rejected",
                AtmException.Reason.LIMIT_EXCEEDED,
                () -> money.withdraw(a.accountNo(), tooBig.toPlainString(), "too big", null));

            List<Txn> history = money.history(a.accountNo(), LocalDate.now().minusDays(2),
                LocalDate.now().plusDays(1), null, 50);
            ok("statement lists the movements (" + history.size() + " found)", history.size() >= 5);

            // the facade the screens use: sign in, read the balance, sign out
            Bank bank = Bank.get();
            Bank.CustomerSession facade = bank.signIn(a.cardNo(), a.pin(), "ATM");
            money("facade reads the same balance", "1110.00", bank.balance(facade));
            ok("facade reports the account limits", bank.limitsText(facade).contains("Per transaction"));
            bank.signOut(facade);
            ok("facade sign-out revokes the session", !bank.sessions().check(facade.sessionId()));
            expect("the facade refuses a revoked session", AtmException.Reason.SESSION_EXPIRED,
                () -> bank.balance(facade));

            expect("first wrong PIN is rejected", AtmException.Reason.AUTHENTICATION,
                () -> auth.signIn(a.cardNo(), "0000", "ATM"));
            expect("second wrong PIN is rejected", AtmException.Reason.AUTHENTICATION,
                () -> auth.signIn(a.cardNo(), "0000", "ATM"));
            expect("third wrong PIN locks the card", AtmException.Reason.LOCKED,
                () -> auth.signIn(a.cardNo(), "0000", "ATM"));
            expect("a locked card cannot sign in even with the right PIN",
                AtmException.Reason.LOCKED, () -> auth.signIn(a.cardNo(), a.pin(), "ATM"));

            OtpService otp = new OtpService();
            otp.issue(b.cardNo(), OtpService.PURPOSE_CARD_BLOCK);
            expect("a wrong verification code is refused", AtmException.Reason.OTP_INVALID,
                () -> cards.blockCard(b.cardNo(), "000000"));
            String code = otp.issue(b.cardNo(), OtpService.PURPOSE_CARD_BLOCK);
            cards.blockCard(b.cardNo(), code);
            ok("card is blocked after a valid code",
                cards.card(b.cardNo()).status() == CardStatus.BLOCKED);
            expect("a blocked card cannot sign in", AtmException.Reason.BLOCKED,
                () -> auth.signIn(b.cardNo(), b.pin(), "ATM"));

            auth.signOut(sessionA.sessionId());
            ok("sign-out revokes the session", !auth.isSessionAlive(sessionA.sessionId()));

            // ------------------------------------------------------------ back office
            AdminService back = bank.admin();
            LocalDate from = LocalDate.now().minusDays(2);
            LocalDate to = LocalDate.now().plusDays(1);

            DashboardStats stats = back.dashboard();
            ok("dashboard counts customers (" + stats.customers() + ")", stats.customers() > 0);
            ok("dashboard shows the machine cash on hand", stats.atmCashOnHand() != null
                && stats.atmCashOnHand().compareTo(BigDecimal.ZERO) > 0);
            ok("dashboard counts the blocked card (" + stats.blockedCards() + ")",
                stats.blockedCards() >= 1);

            ok("customer search finds the account holder",
                !back.searchCustomers(b.cardNo(), null, 10).isEmpty());
            ok("account list is readable", !back.listAccounts("", 10).isEmpty());
            ok("card list is readable", !back.listCards("", 10).isEmpty());
            ok("billers are seeded (" + back.billers().size() + ")", back.billers().size() >= 5);
            ok("machine cash levels are readable (" + back.atmCashLevels().size() + ")",
                back.atmCashLevels().size() >= 5);

            BigDecimal cashBefore = back.atmCashTotal();
            back.restockCash(100, 1, "smoke-teller");
            ok("restocking a note changes the cash on hand",
                back.atmCashTotal().compareTo(cashBefore.add(new BigDecimal("100"))) == 0);

            ok("daily report has rows", !back.dailyReport(from, to).isEmpty());
            ok("type report has rows", !back.typeReport(from, to).isEmpty());
            ok("top customers report has rows", !back.topCustomers(from, to, 5).isEmpty());
            ok("failed logins are recorded", !back.recentFailedLogins(20).isEmpty());
            ok("audit trail holds the movements", !back.recentAudit(AuditService.TRANSFER, null, 50)
                .isEmpty());

            Path csv = back.typeCsv(from, to);
            ok("report exports to " + csv.getFileName(), Files.exists(csv));
            Files.deleteIfExists(csv);

            back.resetCardLock(b.cardNo(), "smoke-teller");
            ok("staff can clear a card lock", back.card(b.cardNo()).failedAttempts() == 0);
        } catch (Throwable t) {
            failed++;
            System.out.println("  FAIL  unexpected error: " + t);
            t.printStackTrace(System.out);
        }

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: OK (" + passed + " checks passed)"
            : "RESULT: FAILED (" + failed + " of " + (passed + failed) + " checks failed)");
        Database.shutdown();
        System.exit(failed == 0 ? 0 : 1);
    }

    private static OnboardingService.NewAccount onboard(OnboardingService service, String name,
            String phone) {
        return service.openAccount(new OnboardingService.Application(
            name, "Tester", "1990-01-01", "Male", name.replace(' ', '.').toLowerCase()
                + "@example.com", "SINGLE", "1 Test Street", "Harare", "None", "General", "50000",
            "Degree", "Engineer", "", "123456789012", phone, "ATM Card", AccountType.SAVING,
            new BigDecimal("1000")));
    }

    private static void counts(Database db) {
        String[] tables = {"customers", "accounts", "cards", "transactions", "audit_log",
            "failed_logins", "admin_users"};
        StringBuilder sb = new StringBuilder("  tables: ");
        for (String table : tables) {
            Number n = db.read(c -> {
                try (var st = c.createStatement();
                     var rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            });
            sb.append(table).append('=').append(n).append("  ");
        }
        System.out.println(sb.toString().trim());
    }

    private static void ok(String label, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + label);
        } else {
            failed++;
            System.out.println("  FAIL  " + label);
        }
    }

    private static void money(String label, String expected, BigDecimal actual) {
        ok(label + " (expected " + expected + ", got " + actual.toPlainString() + ")",
            expected.equals(actual.toPlainString()));
    }

    private static void expect(String label, AtmException.Reason reason, Runnable action) {
        try {
            action.run();
            failed++;
            System.out.println("  FAIL  " + label + " (no error was raised)");
        } catch (AtmException e) {
            ok(label + " [" + e.reason() + "]", e.reason() == reason);
        } catch (Throwable t) {
            failed++;
            System.out.println("  FAIL  " + label + " (unexpected " + t + ")");
        }
    }
}
