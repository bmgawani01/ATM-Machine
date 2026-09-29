package atm.core.model;

/** Card lifecycle. A blocked card can be unblocked by an admin, which is the card-management screen. */
public enum CardStatus {

    ACTIVE("Active"),
    BLOCKED("Blocked"),
    EXPIRED("Expired");

    private final String label;

    CardStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static CardStatus of(String v) {
        for (CardStatus s : values()) {
            if (s.name().equalsIgnoreCase(v)) {
                return s;
            }
        }
        return ACTIVE;
    }
}
