package atm.core.model;

/** Product the customer picked on the signup form. */
public enum AccountType {

    SAVING("Saving Account", "SAVING"),
    FIXED_DEPOSIT("Fixed Deposit Account", "FIXED_DEPOSIT"),
    CURRENT("Current Account", "CURRENT"),
    RECURRING_DEPOSIT("Recurring Deposit Account", "RECURRING_DEPOSIT");

    private final String label;
    private final String code;

    AccountType(String label, String code) {
        this.label = label;
        this.code = code;
    }

    public String label() {
        return label;
    }

    public String code() {
        return code;
    }

    public static AccountType fromLabel(String label) {
        for (AccountType t : values()) {
            if (t.label.equalsIgnoreCase(label) || t.code.equalsIgnoreCase(label)) {
                return t;
            }
        }
        return SAVING;
    }
}
