package atm.system.admin;

import atm.core.Bank;
import atm.core.Ids;
import atm.core.model.Account;
import atm.core.model.AccountType;
import atm.core.model.Limits;
import atm.system.Ui;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * Account management: find an account, then freeze, close, retype it or change its limits.
 * Each of those writes an audit entry against the staff member's name.
 */
public class AdminAccounts extends AdminShell {

    private final JTextField searchField = Ui.textField(20);
    private JTable table;
    private List<Account> current = new ArrayList<>();
    private JPanel detail;

    public AdminAccounts() {
        super("Back office - accounts");
        build(content());
        refresh();
        setVisible(true);
    }

    @Override
    protected void build(JPanel area) {
        JButton find = Ui.button("SEARCH");
        find.addActionListener(e -> Ui.run(this, this::refresh));

        JButton reload = Ui.ghostButton("REFRESH");
        reload.addActionListener(e -> Ui.run(this, this::refresh));

        JPanel filters = new Ui.Form("ACCOUNTS")
            .row("Account number or card number", searchField)
            .buttons(find, reload)
            .panel();
        filters.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        table = Ui.table(new String[] {"Account", "Card", "Type", "Balance", "Status", "Opened"},
            new ArrayList<>(), 700);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelected();
            }
        });

        detail = new JPanel(new GridLayout(0, 2, 8, 4));
        detail.setOpaque(false);

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);
        body.add(Ui.scroll(table), BorderLayout.CENTER);
        body.add(detail, BorderLayout.EAST);
        area.add(body, BorderLayout.CENTER);
        area.add(filters, BorderLayout.NORTH);
    }

    private void refresh() {
        current = Bank.get().admin().listAccounts(text(searchField), 200);
        List<String[]> rows = new ArrayList<>();
        for (Account a : current) {
            rows.add(new String[] {a.accountNo(), Ids.maskCard(a.cardNo()), a.type().label(),
                money(a.balance()), a.status(),
                a.openedAt() == null ? "-" : a.openedAt().toLocalDate().toString()});
        }
        AdminCustomers.fill(table, new String[] {"Account", "Card", "Type", "Balance", "Status",
            "Opened"}, rows);
        status(current.size() + " account(s) matched.");
        showSelected();
    }

    private void showSelected() {
        detail.removeAll();
        int row = table.getSelectedRow();
        if (row < 0 || row >= current.size()) {
            detail.add(Ui.hint("Select an account to manage it."));
            detail.revalidate();
            detail.repaint();
            return;
        }
        Account a = current.get(row);
        String actor = AdminSession.require().username();
        Limits l = Bank.get().admin().limits(a.accountNo());

        detail.add(Ui.heading(a.accountNo()));
        detail.add(Ui.hint("Balance: " + money(a.balance())));
        detail.add(Ui.hint("Type: " + a.type().label()));
        detail.add(Ui.hint("Status: " + a.status()));
        detail.add(Ui.hint("Per txn: " + money(l.perTxn())));
        detail.add(Ui.hint("Daily withdrawal: " + money(l.dailyWithdrawal())));

        detail.add(statusChanger(a, actor));
        detail.add(typeChanger(a, actor));
        detail.add(limitEditor(a, l, actor));

        detail.revalidate();
        detail.repaint();
    }

    private JPanel statusChanger(Account a, String actor) {
        JComboBox<String> box = Ui.combo(new String[] {"ACTIVE", "FROZEN", "CLOSED"});
        box.setSelectedItem(a.status());
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setOpaque(false);
        JButton go = Ui.button("SET STATUS");
        go.addActionListener(e -> Ui.run(this, () -> {
            Bank.get().admin().setAccountStatus(a.accountNo(), (String) box.getSelectedItem(),
                actor);
            Ui.info(this, a.accountNo() + " is now " + box.getSelectedItem() + ".");
            refresh();
        }));
        p.add(Ui.hint("Account status"), BorderLayout.NORTH);
        p.add(box, BorderLayout.CENTER);
        p.add(go, BorderLayout.SOUTH);
        return p;
    }

    private JPanel typeChanger(Account a, String actor) {
        String[] types = new String[AccountType.values().length];
        for (int i = 0; i < types.length; i++) {
            types[i] = AccountType.values()[i].label();
        }
        JComboBox<String> box = Ui.combo(types);
        box.setSelectedItem(a.type().label());
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setOpaque(false);
        JButton go = Ui.button("SET TYPE");
        go.addActionListener(e -> Ui.run(this, () -> {
            Bank.get().admin().setAccountType(a.accountNo(),
                AccountType.fromLabel((String) box.getSelectedItem()), actor);
            Ui.info(this, a.accountNo() + " is now a "
                + box.getSelectedItem() + ".");
            refresh();
        }));
        p.add(Ui.hint("Account type"), BorderLayout.NORTH);
        p.add(box, BorderLayout.CENTER);
        p.add(go, BorderLayout.SOUTH);
        return p;
    }

    private JPanel limitEditor(Account a, Limits l, String actor) {
        JTextField perTxn = Ui.textField(10);
        perTxn.setText(l.perTxn().toPlainString());
        JTextField daily = Ui.textField(10);
        daily.setText(l.dailyWithdrawal().toPlainString());
        JTextField otp = Ui.textField(10);
        otp.setText(l.requireOtpAbove() == null ? "" : l.requireOtpAbove().toPlainString());

        JButton save = Ui.button("SAVE LIMITS");
        save.addActionListener(e -> Ui.run(this, () -> {
            Bank.get().admin().setLimits(a.accountNo(), new BigDecimal(text(perTxn)),
                new BigDecimal(text(daily)), l.dailyTransfers(), l.dailyBillPayments(),
                text(otp).isEmpty() ? null : new BigDecimal(text(otp)), actor);
            Ui.info(this, "Limits updated for " + a.accountNo() + ".");
            refresh();
        }));

        JPanel fields = new JPanel(new GridLayout(3, 2, 4, 2));
        fields.setOpaque(false);
        fields.add(Ui.hint("Per txn"));
        fields.add(perTxn);
        fields.add(Ui.hint("Daily withdrawal"));
        fields.add(daily);
        fields.add(Ui.hint("OTP above"));
        fields.add(otp);

        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setOpaque(false);
        p.add(Ui.hint("Limits"), BorderLayout.NORTH);
        p.add(fields, BorderLayout.CENTER);
        p.add(save, BorderLayout.SOUTH);
        return p;
    }

    public static void main(String[] args) {
        AdminSession.open(Bank.get().auth().signInAdmin("admin", "1234"));
        new AdminAccounts();
    }
}
