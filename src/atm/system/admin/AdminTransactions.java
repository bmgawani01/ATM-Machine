package atm.system.admin;

import atm.core.Bank;
import atm.core.Ids;
import atm.core.model.Txn;
import atm.core.model.TransactionType;
import atm.system.Ui;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * Transaction monitoring. Either the latest movements across the whole bank, or every
 * movement on one account over a date range.
 */
public class AdminTransactions extends AdminShell {

    private final JTextField accountField = Ui.textField(20);
    private final JTextField fromField = Ui.textField(12);
    private final JTextField toField = Ui.textField(12);
    private final JComboBox<String> typeBox = typeChoices();
    private JTable table;

    public AdminTransactions() {
        super("Back office - transactions");
        fromField.setText(LocalDate.now().minusDays(7).toString());
        toField.setText(LocalDate.now().toString());
        build(content());
        refresh();
        setVisible(true);
    }

    private static JComboBox<String> typeChoices() {
        String[] types = new String[TransactionType.values().length + 1];
        types[0] = "All types";
        for (int i = 0; i < TransactionType.values().length; i++) {
            types[i + 1] = TransactionType.values()[i].label();
        }
        return Ui.combo(types);
    }

    @Override
    protected void build(JPanel area) {
        JButton run = Ui.button("RUN");
        run.addActionListener(e -> Ui.run(this, this::refresh));
        JButton all = Ui.ghostButton("WHOLE BANK");
        all.addActionListener(e -> {
            accountField.setText("");
            refresh();
        });

        JPanel filters = new Ui.Form("TRANSACTION MONITOR")
            .subtitle("Leave the account blank to monitor the whole bank.")
            .row("Account number (optional)", accountField)
            .row("From (yyyy-mm-dd)", fromField)
            .row("To (yyyy-mm-dd)", toField)
            .row("Type", typeBox)
            .buttons(run, all)
            .panel();
        filters.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        table = Ui.table(new String[] {"When", "Account", "Type", "Amount", "Balance after",
            "Reference", "Channel", "Status"}, new ArrayList<>(), 1000);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(filters, BorderLayout.NORTH);
        body.add(Ui.scroll(table), BorderLayout.CENTER);
        area.add(body, BorderLayout.CENTER);
    }

    private void refresh() {
        List<Txn> txns;
        String account = text(accountField);
        if (account.isEmpty()) {
            txns = Bank.get().admin().recentTransactions(300);
        } else {
            LocalDate from = date(fromField, LocalDate.now().minusDays(7));
            LocalDate to = date(toField, LocalDate.now());
            TransactionType type = typeBox.getSelectedIndex() == 0 ? null
                : TransactionType.values()[typeBox.getSelectedIndex() - 1];
            txns = Bank.get().admin().accountTransactions(account, from, to, type, 500);
        }

        List<String[]> rows = new ArrayList<>();
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (Txn t : txns) {
            if (account.isEmpty() && typeBox.getSelectedIndex() > 0
                && t.type() != TransactionType.values()[typeBox.getSelectedIndex() - 1]) {
                continue;
            }
            rows.add(new String[] {
                stamp(t.createdAt()),
                Ids.maskAccount(t.accountNo()),
                t.type().label(),
                t.signedAmount(),
                money(t.balanceAfter()),
                Ui.orDash(t.reference()),
                Ui.orDash(t.channel()),
                Ui.orDash(t.status())
            });
            total = total.add(t.amount());
        }
        AdminCustomers.fill(table, new String[] {"When", "Account", "Type", "Amount",
            "Balance after", "Reference", "Channel", "Status"}, rows);
        status(rows.size() + " movement(s) shown, " + money(total) + " gross value.");
    }

    private static LocalDate date(JTextField field, LocalDate fallback) {
        try {
            return LocalDate.parse(text(field));
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    public static void main(String[] args) {
        AdminSession.open(Bank.get().auth().signInAdmin("admin", "1234"));
        new AdminTransactions();
    }
}
