package atm.system.admin;

import atm.core.Bank;
import atm.core.Ids;
import atm.core.dao.AdminUserDao;
import atm.core.model.Account;
import atm.core.model.AccountSummary;
import atm.core.model.Card;
import atm.core.model.Customer;
import atm.system.Ui;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * Customer management: search, then act on the selected customer.
 *
 * <p>Every action here goes through {@code atm.core.AdminService}, which writes an audit
 * entry naming the staff member who made the change, so the security screen can answer
 * "who froze this account".
 */
public class AdminCustomers extends AdminShell {

    private final JTextField searchField = Ui.textField(20);
    private final JComboBox<String> statusBox =
        Ui.combo(new String[] {"Any status", "ACTIVE", "BLOCKED", "CLOSED"});
    private JTable table;
    private List<Customer> current = new ArrayList<>();
    private JPanel detail;

    public AdminCustomers() {
        super("Back office - customers");
        build(content());
        refresh();
        setVisible(true);
    }

    /** Opens straight onto one customer, used when arriving from the accounts screen. */
    public AdminCustomers(String cardNo) {
        this();
        searchField.setText(cardNo);
        refresh();
    }

    @Override
    protected void build(JPanel area) {
        JButton find = Ui.button("SEARCH");
        find.addActionListener(e -> Ui.run(this, this::refresh));
        JButton clear = Ui.ghostButton("CLEAR");
        clear.addActionListener(e -> {
            searchField.setText("");
            refresh();
        });

        JPanel filters = new Ui.Form("CUSTOMER DIRECTORY")
            .row("Name, card, email or phone", searchField)
            .row("Status", statusBox)
            .buttons(find, clear)
            .panel();
        filters.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        table = Ui.table(new String[] {"Card", "Name", "Email", "Phone", "Account type",
            "Status", "Opened"}, new ArrayList<>(), 700);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelected();
            }
        });

        detail = new JPanel(new GridLayout(0, 2, 8, 4));
        detail.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(filters, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);
        body.add(titled("Search results", table), BorderLayout.CENTER);
        body.add(titled("Selected customer", detail), BorderLayout.EAST);
        top.add(body, BorderLayout.CENTER);
        area.add(top, BorderLayout.CENTER);
    }

    private JPanel titled(String caption, java.awt.Component c) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(Ui.heading(caption), BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private void refresh() {
        String status = statusBox.getSelectedIndex() == 0 ? null
            : (String) statusBox.getSelectedItem();
        current = Bank.get().admin().searchCustomers(text(searchField), status, 200);
        List<String[]> rows = new ArrayList<>();
        for (Customer c : current) {
            rows.add(new String[] {Ids.maskCard(c.cardNo()), c.name(), Ui.orDash(c.email()),
                Ui.orDash(c.phone()), Ui.orDash(c.accountType()), c.status(),
                c.createdAt() == null ? "-" : c.createdAt().toLocalDate().toString()});
        }
        fill(table, new String[] {"Card", "Name", "Email", "Phone", "Account type",
            "Status", "Opened"}, rows);
        status(current.size() + " customer(s) matched.");
        showSelected();
    }

    private void showSelected() {
        detail.removeAll();
        int row = table.getSelectedRow();
        if (row < 0 || row >= current.size()) {
            detail.add(Ui.hint("Select a customer to see their details and actions."));
            detail.revalidate();
            detail.repaint();
            return;
        }
        Customer c = current.get(row);
        AccountSummary s = Bank.get().admin().summary(c.cardNo());
        String actor = AdminSession.require().username();

        detail.add(Ui.heading(c.name()));
        detail.add(field("Card", Ids.maskCard(c.cardNo())));
        detail.add(field("Email", Ui.orDash(c.email())));
        detail.add(field("Phone", Ui.orDash(c.phone())));
        detail.add(field("Status", c.status()));

        if (s != null) {
            Account a = s.account();
            Card card = s.card();
            detail.add(field("Account", a.accountNo()));
            detail.add(field("Balance", money(a.balance())));
            detail.add(field("Account status", a.status()));
            detail.add(field("Card status", card == null ? "-" : card.status().label()));
        }

        JButton block = Ui.ghostButton("BLOCK CUSTOMER");
        block.addActionListener(e -> Ui.run(this, () -> {
            Bank.get().admin().setCustomerStatus(c.cardNo(), "BLOCKED", actor);
            Ui.info(this, c.name() + " is now blocked.");
            refresh();
        }));
        JButton activate = Ui.ghostButton("ACTIVATE");
        activate.addActionListener(e -> Ui.run(this, () -> {
            Bank.get().admin().setCustomerStatus(c.cardNo(), "ACTIVE", actor);
            Ui.info(this, c.name() + " is now active.");
            refresh();
        }));

        JPanel actions = new JPanel(new GridLayout(1, 2, 6, 0));
        actions.setOpaque(false);
        actions.add(block);
        actions.add(activate);
        detail.add(actions);
        detail.revalidate();
        detail.repaint();
    }

    private JLabel field(String caption, String value) {
        return Ui.hint(caption + ":  " + value);
    }

    static void fill(JTable table, String[] headers, List<String[]> rows) {
        javax.swing.table.DefaultTableModel model =
            new javax.swing.table.DefaultTableModel(headers, 0) {
                @Override
                public boolean isCellEditable(int r, int c) {
                    return false;
                }
            };
        rows.forEach(model::addRow);
        table.setModel(model);
    }

    public static void main(String[] args) {
        AdminSession.open(Bank.get().auth().signInAdmin("admin", "1234"));
        new AdminCustomers();
    }
}
