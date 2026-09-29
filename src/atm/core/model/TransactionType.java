package atm.core.model;

/** Every money movement the bank records. Direction is derived, never stored twice. */
public enum TransactionType {

    DEPOSIT("Deposit", "+", false),
    WITHDRAWAL("Cash Withdrawal", "-", true),
    FAST_CASH("Fast Cash", "-", true),
    TRANSFER_OUT("Fund Transfer Out", "-", true),
    TRANSFER_IN("Fund Transfer In", "+", false),
    BILL_PAYMENT("Bill Payment", "-", true),
    AIRTIME("Airtime Purchase", "-", true);

    private final String label;
    private final String sign;
    private final boolean debit;

    TransactionType(String label, String sign, boolean debit) {
        this.label = label;
        this.sign = sign;
        this.debit = debit;
    }

    public String label() {
        return label;
    }

    public String sign() {
        return sign;
    }

    /** True when the amount leaves the account. */
    public boolean isDebit() {
        return debit;
    }

    public static TransactionType of(String value) {
        for (TransactionType t : values()) {
            if (t.name().equalsIgnoreCase(value)) {
                return t;
            }
        }
        return DEPOSIT;
    }
}
