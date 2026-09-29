package atm.core.model;

/** Utility company the customer can pay from the ATM. */
public record Biller(long id, String name, String category, String customerRefLabel) {
}
