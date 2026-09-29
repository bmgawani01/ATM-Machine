package atm.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Logging facade on top of {@code java.util.logging}, which ships with the JDK, so the app
 * has no logging dependency of its own and the same code works on the desktop and on the
 * web tier.
 *
 * <p>The console copy is printed here instead of by JUL's default handler so a screen can
 * log an error quietly when it needs to. Logging never throws: a failure to log must never
 * break a money movement.
 */
public final class Log {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final java.util.logging.Logger LOGGER =
        java.util.logging.Logger.getLogger("atm");

    static {
        // we print the console copy ourselves, so JUL's default handler is switched off
        LOGGER.setUseParentHandlers(false);
    }

    private Log() {
    }

    public static void info(String msg) {
        write("INFO", msg, null, true);
    }

    public static void warn(String msg) {
        write("WARN", msg, null, true);
    }

    public static void error(String msg, Throwable t) {
        write("ERROR", msg, t, true);
    }

    public static void error(String msg) {
        write("ERROR", msg, null, true);
    }

    /** Same as error(String, Throwable) but does not print the stack trace on screen. */
    public static void errorQuiet(String msg, Throwable t) {
        write("ERROR", msg, t, false);
    }

    private static void write(String level, String msg, Throwable t, boolean onScreen) {
        String line = LocalDateTime.now().format(TS) + " [" + Thread.currentThread().getName()
            + "] " + level + " " + msg;
        try {
            LOGGER.log(levelOf(level), line, t);
        } catch (Exception ignored) {
            // never let logging break the app
        }
        if (onScreen) {
            System.out.println(line);
            if (t != null) {
                System.out.println(stackTrace(t));
            }
        }
    }

    private static Level levelOf(String level) {
        switch (level) {
            case "ERROR":
                return Level.SEVERE;
            case "WARN":
                return Level.WARNING;
            default:
                return Level.INFO;
        }
    }

    public static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
