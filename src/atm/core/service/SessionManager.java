package atm.core.service;

import atm.core.AtmException;
import atm.core.Config;
import atm.core.Log;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Automatic session timeout.
 *
 * <p>Three parts working together: the {@code sessions} row carries a deadline that slides
 * forward on each action, this watchdog purges dead rows in the background, and anything
 * holding a session asks {@link #require} before it does work, so an abandoned machine
 * cannot be used by the next person who walks up to it.
 */
public final class SessionManager {

    /** Told once per session when the deadline passes, so a screen can return to sign-in. */
    public interface Listener {
        void onSessionExpired(String sessionId);
    }

    private final AuthService auth = new AuthService();
    private final Set<Listener> listeners = ConcurrentHashMap.newKeySet();
    private final Set<String> announced = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService watchdog;

    /** Starts the background purge of expired sessions. Safe to call more than once. */
    public synchronized void start() {
        if (watchdog != null) {
            return;
        }
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "atm-session-watchdog");
            t.setDaemon(true);
            return t;
        });
        long seconds = Math.max(10, Config.getInt(Config.Setting.SESSION_TIMEOUT_SECONDS, 120) / 2);
        watchdog.scheduleWithFixedDelay(this::purgeQuietly, seconds, seconds, TimeUnit.SECONDS);
        Log.info("Session watchdog started (every " + seconds + "s)");
    }

    public synchronized void stop() {
        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }
    }

    private void purgeQuietly() {
        try {
            auth.purgeOldSessions();
        } catch (RuntimeException e) {
            Log.warn("Session purge failed: " + e.getMessage());
        }
    }

    public void register(Listener listener) {
        listeners.add(listener);
    }

    public void unregister(Listener listener) {
        listeners.remove(listener);
    }

    /** @return true while the session is alive; notifies the listeners once when it is not */
    public boolean check(String sessionId) {
        boolean alive = sessionId != null && auth.isSessionAlive(sessionId);
        if (!alive) {
            announce(sessionId);
        }
        return alive;
    }

    /**
     * The call every money movement makes first.
     *
     * @throws AtmException SESSION_EXPIRED, after telling the screens to go back to sign-in
     */
    public void require(String sessionId) {
        if (sessionId == null || !auth.isSessionAlive(sessionId)) {
            announce(sessionId);
            throw new AtmException(AtmException.Reason.SESSION_EXPIRED,
                "Your session timed out. Please sign in again");
        }
    }

    public int timeoutSeconds() {
        return auth.sessionTimeoutSeconds();
    }

    public int secondsLeft(String sessionId) {
        LocalDateTime expires = auth.expiresAt(sessionId);
        if (expires == null) {
            return 0;
        }
        long seconds = Duration.between(LocalDateTime.now(), expires).getSeconds();
        return (int) Math.max(0, Math.min(seconds, Integer.MAX_VALUE));
    }

    public void signOut(String sessionId) {
        auth.signOut(sessionId);
        announced.remove(sessionId);
    }

    /** A new sign-in clears any earlier expiry notice for the same id. */
    public void opened(String sessionId) {
        announced.remove(sessionId);
    }

    private void announce(String sessionId) {
        if (sessionId == null || !announced.add(sessionId)) {
            return;
        }
        Log.info("Session " + shortId(sessionId) + " expired, returning to sign-in");
        for (Listener l : listeners) {
            try {
                l.onSessionExpired(sessionId);
            } catch (RuntimeException e) {
                Log.errorQuiet("Session listener failed", e);
            }
        }
    }

    private static String shortId(String id) {
        return id == null ? "-" : id.substring(0, Math.min(8, id.length()));
    }
}
