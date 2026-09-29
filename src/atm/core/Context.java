package atm.core;

/**
 * Who is calling and from where. The desktop app leaves the defaults; the web tier sets
 * these per request so the audit trail says "which teller, from which IP", not "null".
 */
public final class Context {

    /** Where the request came from: DESKTOP, WEB, ATM. */
    public static final String CHANNEL_DESKTOP = "DESKTOP";
    public static final String CHANNEL_WEB = "WEB";

    private static final ThreadLocal<String> CHANNEL = new ThreadLocal<>();
    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();
    private static final ThreadLocal<String> IP = new ThreadLocal<>();

    private Context() {
    }

    public static void set(String channel, String actor, String ip) {
        CHANNEL.set(channel);
        ACTOR.set(actor);
        IP.set(ip);
    }

    public static void clear() {
        CHANNEL.remove();
        ACTOR.remove();
        IP.remove();
    }

    public static String channel() {
        String c = CHANNEL.get();
        return c == null ? CHANNEL_DESKTOP : c;
    }

    public static String actor() {
        String a = ACTOR.get();
        return a == null ? channel() : a;
    }

    public static String ip() {
        String i = IP.get();
        return i == null || i.isBlank() ? "127.0.0.1" : i;
    }
}
