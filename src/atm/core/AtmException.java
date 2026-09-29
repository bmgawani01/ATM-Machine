package atm.core;

/**
 * Every error the app raises on purpose. {@link #reason()} lets callers (screens, REST
 * controllers) map an error to a message or an HTTP status without string matching, and
 * {@link #userMessage()} is safe to show to a bank customer - it never leaks SQL or a password.
 */
public class AtmException extends RuntimeException {

    public enum Reason {
        VALIDATION,
        AUTHENTICATION,
        LOCKED,
        BLOCKED,
        INSUFFICIENT_FUNDS,
        LIMIT_EXCEEDED,
        NOT_FOUND,
        DUPLICATE,
        OTP_INVALID,
        SESSION_EXPIRED,
        DATABASE,
        UNAVAILABLE
    }

    private final Reason reason;

    public AtmException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AtmException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** Text for the message box / API response. */
    public String userMessage() {
        String m = getMessage();
        return (m == null || m.isEmpty()) ? defaultText() : m;
    }

    private String defaultText() {
        switch (reason) {
            case VALIDATION:
                return "Check the details you entered";
            case AUTHENTICATION:
                return "Incorrect card number or PIN";
            case LOCKED:
                return "Card locked. Try again later";
            case BLOCKED:
                return "This card is blocked";
            case INSUFFICIENT_FUNDS:
                return "Insufficient balance";
            case LIMIT_EXCEEDED:
                return "Transaction limit exceeded";
            case NOT_FOUND:
                return "Not found";
            case DUPLICATE:
                return "That record already exists";
            case OTP_INVALID:
                return "Invalid or expired verification code";
            case SESSION_EXPIRED:
                return "Your session timed out. Please sign in again";
            case UNAVAILABLE:
                return "The service is temporarily unavailable";
            default:
                return "Something went wrong. Please try again";
        }
    }

    /** Something the user typed is not acceptable. */
    public static class Validation extends AtmException {
        public Validation(String message) {
            super(Reason.VALIDATION, message);
        }
    }

    /** Credentials were rejected. */
    public static class Auth extends AtmException {
        public Auth(String message) {
            super(Reason.AUTHENTICATION, message);
        }
    }
}
