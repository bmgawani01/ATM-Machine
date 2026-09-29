package atm.system;

import atm.core.AtmException;
import atm.core.Bank;
import atm.core.Log;

/**
 * Who is using the app right now.
 *
 * <p>The original screens were handed the customer's PIN and used it as their only
 * identifier. The screens still take that PIN in their constructor, but the real authority
 * is the server-side session created at sign-in: it carries the account number, expires on
 * its own, and is revoked the moment the PIN or the card changes. Every screen therefore
 * asks for the session here instead of trusting a string that was passed along.
 */
public final class AppSession {

    private static volatile Bank.CustomerSession current;
    private static volatile String pin = "";
    private static volatile javax.swing.Timer heartbeat;

    private AppSession() {
    }

    /** Signs in and remembers the session. Called by {@link Login}. */
    public static void open(String cardNo, String pin, String channel) {
        Bank.CustomerSession session = Bank.get().signIn(cardNo, pin, channel);
        current = session;
        AppSession.pin = pin == null ? "" : pin.trim();
        startHeartbeat();
    }

    /**
     * The session for a screen that was opened with {@code pin}.
     *
     * @throws AtmException SESSION_EXPIRED when nobody is signed in or the session died,
     *     VALIDATION when the screen was opened for a different card
     */
    public static Bank.CustomerSession require(String expectedPin) {
        Bank.CustomerSession session = current;
        if (session == null) {
            throw new AtmException(AtmException.Reason.SESSION_EXPIRED,
                "Your session timed out. Please sign in again");
        }
        String expected = expectedPin == null ? "" : expectedPin.trim();
        if (!expected.isEmpty() && !expected.equals(pin)) {
            throw new AtmException(AtmException.Reason.VALIDATION,
                "This screen was opened for a different card");
        }
        if (!Bank.get().sessions().check(session.sessionId())) {
            throw new AtmException(AtmException.Reason.SESSION_EXPIRED,
                "Your session timed out. Please sign in again");
        }
        return session;
    }

    public static Bank.CustomerSession current() {
        return require(null);
    }

    /** The PIN the signed-in customer used; screens keep passing it around as before. */
    public static String pin() {
        return pin;
    }

    public static boolean isSignedIn() {
        Bank.CustomerSession session = current;
        return session != null && Bank.get().sessions().check(session.sessionId());
    }

    public static void clear() {
        current = null;
        pin = "";
        stopHeartbeat();
    }

    /** Signs out properly: tells the server, then forgets the session locally. */
    public static void signOut() {
        Bank.CustomerSession session = current;
        current = null;
        pin = "";
        stopHeartbeat();
        if (session != null) {
            try {
                Bank.get().signOut(session);
            } catch (RuntimeException e) {
                Log.warn("Sign-out failed: " + e.getMessage());
            }
        }
    }

    /**
     * An idle screen has no events of its own, so an expired session would otherwise go
     * unnoticed until the customer pressed something. This checks the session on a timer and
     * sends the customer back to sign-in the moment it has died.
     */
    private static void startHeartbeat() {
        if (heartbeat != null) {
            return;
        }
        javax.swing.Timer timer = new javax.swing.Timer(15_000, e -> {
            Bank.CustomerSession session = current;
            if (session == null) {
                stopHeartbeat();
                return;
            }
            if (Bank.get().sessions().check(session.sessionId())) {
                return;
            }
            stopHeartbeat();
            current = null;
            pin = "";
            Ui.closeAll();
            Ui.error(null, "Your session timed out after a period of inactivity.\n"
                + "Please sign in again.");
            new Login();
        });
        timer.setRepeats(true);
        timer.start();
        heartbeat = timer;
    }

    private static void stopHeartbeat() {
        javax.swing.Timer timer = heartbeat;
        heartbeat = null;
        if (timer != null) {
            timer.stop();
        }
    }

    /**
     * Makes the per-class {@code main} methods work when they are launched on their own:
     * signs in with the demo card if nobody is signed in yet.
     */
    public static void ensureDemo() {
        if (isSignedIn()) {
            return;
        }
        try {
            open("1234567890", "1234", "ATM");
        } catch (AtmException e) {
            Log.warn("Demo sign-in failed: " + e.userMessage());
        }
    }
}
