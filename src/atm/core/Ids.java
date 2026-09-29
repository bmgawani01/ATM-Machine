package atm.core;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Identifiers: account numbers, card numbers, session ids, idempotency keys, report ranges. */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private Ids() {
    }

    /** 12-digit account number, e.g. 100000004271. */
    public static String accountNo() {
        return "10" + String.format("%010d", RANDOM.nextInt(1_000_000_000));
    }

    /** 16-digit card number, unique per customer, like a real PAN. */
    public static String cardNo() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%04d", RANDOM.nextInt(10_000)));
        }
        return sb.toString();
    }

    /** Opaque session id. */
    public static String sessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Key that makes a money movement happen at most once. The screens generate one per
     * button press, so a double click, a retry after a flaky network, or a refresh of a
     * REST request can never charge the customer twice.
     */
    public static String idempotencyKey() {
        return UUID.randomUUID().toString();
    }

    /** Reference shown on receipts, e.g. TXN2609097F3A2. */
    public static String reference() {
        return "TXN" + LocalDate.now().format(COMPACT) + randomSuffix();
    }

    public static String receiptNo() {
        return "RCP" + randomSuffix();
    }

    public static String billRef() {
        return "BILL" + randomSuffix();
    }

    public static String airtimeRef() {
        return "AIR" + randomSuffix();
    }

    public static String employeeId() {
        return "EMP" + String.format("%04d", 1000 + RANDOM.nextInt(9000));
    }

    private static String randomSuffix() {
        return Integer.toHexString(RANDOM.nextInt(0xFFFFF)).toUpperCase();
    }

    /** True when the string looks like a card number the app can search for. */
    public static boolean looksLikeCardNo(String s) {
        return s != null && s.matches("\\d{4,19}");
    }

    public static String maskCard(String cardNo) {
        if (cardNo == null || cardNo.length() < 5) {
            return "XXXX-XXXX-XXXX-XXXX";
        }
        return cardNo.substring(0, 4) + "-XXXX-XXXX-" + cardNo.substring(cardNo.length() - 4);
    }

    /** Hides the middle of an account number for logs, receipts and the audit trail. */
    public static String maskAccount(String accountNo) {
        if (accountNo == null || accountNo.length() < 5) {
            return "****";
        }
        return accountNo.substring(0, 4) + "****" + accountNo.substring(accountNo.length() - 4);
    }
}
