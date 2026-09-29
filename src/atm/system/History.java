package atm.system;

import atm.core.Bank;
import atm.core.Ids;
import atm.core.Money;
import atm.core.model.TransactionType;
import atm.core.model.Txn;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;

/**
 * Full transaction history with filters, the totals for the period, and CSV export.
 *
 * <p>Filtering and totals are done by {@code atm.core.StatementService} on the session's own
 * account, so the screen can only ever show the signed-in customer's movements.
 */
public class History extends JFrame {

    private final Bank.CustomerSession session;
    private JComboBox<String> typeBox;
    private JComboBox<String> periodBox;
    private JTable table;
    private JLabel totals;

    History(String pin) {
        this.session = AppSession.require(pin);

        String[] types = new String[TransactionType.values().length + 1];
        types[0] = "All types";
        int i = 1;
        for (TransactionType t : TransactionType.values()) {
            types[i++] = t.label();
        }
        typeBox = Ui.combo(types);
        periodBox = Ui.combo(new String[] {"Last 7 days", "Last 30 days", "Last 90 days",
            "This month", "Everything"});

        JButton refresh = Ui.button("APPLY");
        JButton export = Ui.ghostButton("EXPORT CSV");

        refresh.addActionListener(e -> Ui.run(this, this::load));
        export.addActionListener(e -> Ui.run(this, this::export));

        table = Ui.table(new String[] {"Date", "Type", "Amount", "Balance", "Reference", "Note"},
            new ArrayList<>(), 900);
        totals = Ui.hint("");

        Ui.Form form = new Ui.Form("TRANSACTION HISTORY")
            .subtitle(session.name() + "   |   " + Ids.maskAccount(session.accountNo()))
            .row("Period", periodBox)
            .row("Type", typeBox)
            .buttons(refresh, export, Ui.backButton(this, pin));

        setLayout(new java.awt.BorderLayout(0, 0));
        JPanel top = new JPanel(new java.awt.BorderLayout());
        top.setBackground(java.awt.Color.WHITE);
        top.add(form.panel(), java.awt.BorderLayout.NORTH);
        totals.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 22, 8, 22));
        top.add(totals, java.awt.BorderLayout.CENTER);
        top.add(Ui.scroll(table), java.awt.BorderLayout.SOUTH);

        setTitle("ATM - Transaction history");
        setSize(980, 780);
        setLocationRelativeTo(null);
        getContentPane().setBackground(java.awt.Color.WHITE);
        setContentPane(top);

        load();
        setVisible(true);
    }

    private void load() {
        LocalDate today = LocalDate.now();
        LocalDate from = switch (periodBox.getSelectedIndex()) {
            case 0 -> today.minusDays(6);
            case 1 -> today.minusDays(29);
            case 2 -> today.minusDays(89);
            case 3 -> today.withDayOfMonth(1);
            default -> LocalDate.of(2000, 1, 1);
        };
        TransactionType type = typeBox.getSelectedIndex() == 0 ? null
            : TransactionType.values()[typeBox.getSelectedIndex() - 1];

        List<Txn> txns = Bank.get().history(session, from, today, type, 500);
        List<String[]> rows = new ArrayList<>();
        java.math.BigDecimal moneyIn = java.math.BigDecimal.ZERO;
        java.math.BigDecimal moneyOut = java.math.BigDecimal.ZERO;
        for (Txn t : txns) {
            rows.add(new String[] {
                t.createdAt().toLocalDate().toString(),
                t.type().label(),
                t.signedAmount(),
                Money.usd(t.balanceAfter()),
                Ui.orDash(t.reference()),
                Ui.orDash(t.note())
            });
            if (t.isCredit()) {
                moneyIn = moneyIn.add(t.amount());
            } else {
                moneyOut = moneyOut.add(t.amount());
            }
        }
        javax.swing.table.DefaultTableModel model =
            (javax.swing.table.DefaultTableModel) table.getModel();
        model.setRowCount(0);
        rows.forEach(model::addRow);

        totals.setText(rows.size() + " transactions from " + from + " to " + today
            + "     in: " + Money.usd(moneyIn) + "     out: " + Money.usd(moneyOut)
            + "     balance now: " + Money.usd(Bank.get().balance(session)));
    }

    private void export() {
        LocalDate today = LocalDate.now();
        Path file = Bank.get().exportStatement(session, LocalDate.of(2000, 1, 1), today);
        Ui.info(this, "Statement written to:\n" + file);
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new History(AppSession.pin());
    }
}
