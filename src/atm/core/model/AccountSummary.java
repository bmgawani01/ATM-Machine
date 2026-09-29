package atm.core.model;

/** Customer + account + card, the row shape used by the admin list screens. */
public record AccountSummary(Customer customer, Account account, Card card) {

    public String name() {
        return customer.name();
    }

    public String cardNo() {
        return customer.cardNo();
    }

    public String accountNo() {
        return account.accountNo();
    }
}
