package atm.system.admin;

import atm.core.Bank;
import atm.core.model.DashboardStats;
import atm.system.Ui;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;

/** The landing screen for staff: headline numbers, then today's movements and machine cash. */
public class AdminDashboard extends AdminShell {

    public AdminDashboard() {
        super("Back office - dashboard");
        build(content());
        setVisible(true);
    }

    @Override
    protected void build(JPanel area) {
        DashboardStats s = Bank.get().admin().dashboard();

        JPanel tiles = new JPanel(new GridLayout(2, 4, 12, 12));
        tiles.setOpaque(false);
        tiles.add(tile("Customers", String.valueOf(s.customers())));
        tiles.add(tile("Accounts", String.valueOf(s.accounts())));
        tiles.add(tile("Cards active", s.activeCards() + " / " + (s.activeCards() + s.blockedCards())));
        tiles.add(tile("Total balances", money(Bank.get().admin().totalBalances())));
        tiles.add(tile("Transactions today", String.valueOf(s.transactionsToday())));
        tiles.add(tile("Volume today", money(s.volumeToday())));
        tiles.add(tile("Deposits today", money(s.depositsToday())));
        tiles.add(tile("Withdrawals today", money(s.withdrawalsToday())));
        tiles.add(tile("Failed sign-ins today", String.valueOf(s.failedLoginsToday())));
        tiles.add(tile("Cash in the machine", money(s.atmCashOnHand())));
        area.add(tiles, java.awt.BorderLayout.NORTH);

        JPanel tables = new JPanel(new java.awt.BorderLayout(12, 0));
        tables.setOpaque(false);

        List<String[]> recent = new ArrayList<>();
        for (var t : Bank.get().admin().recentTransactions(12)) {
            recent.add(new String[] {
                stamp(t.createdAt()),
                atm.core.Ids.maskAccount(t.accountNo()),
                t.type().label(),
                t.signedAmount(),
                money(t.balanceAfter()),
                atm.core.Ids.maskCard(t.channel())
            });
        }
        tables.add(titled("Latest transactions",
            Ui.table(new String[] {"When", "Account", "Type", "Amount", "Balance", "Channel"},
                recent, 600)), java.awt.BorderLayout.WEST);

        List<String[]> cash = new ArrayList<>();
        for (var c : Bank.get().admin().atmCashLevels()) {
            cash.add(new String[] {String.valueOf(c.denom()), String.valueOf(c.notes()),
                money(c.value()), c.updatedAt() == null ? "-"
                    : stamp(c.updatedAt())});
        }
        tables.add(titled("Machine cash by denomination",
            Ui.table(new String[] {"Denom", "Notes", "Value", "Updated"}, cash, 420)),
            java.awt.BorderLayout.EAST);
        area.add(tables, java.awt.BorderLayout.CENTER);

        boolean healthy = Bank.get().admin().healthy();
        status((healthy ? "Database connected. " : "DATABASE PROBLEM. ")
            + "Pool " + Bank.get().admin().poolActive() + " busy / "
            + Bank.get().admin().poolIdle() + " idle. Report date "
            + LocalDate.now() + ".");
    }

    private static JPanel tile(String caption, String value) {
        JPanel p = new JPanel(new java.awt.BorderLayout());
        p.setBackground(java.awt.Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Ui.LINE),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel v = new JLabel(value == null ? "-" : value);
        v.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 20));
        v.setForeground(Ui.INK);
        JLabel c = Ui.hint(caption);
        p.add(v, java.awt.BorderLayout.CENTER);
        p.add(c, java.awt.BorderLayout.SOUTH);
        return p;
    }

    private static JPanel titled(String caption, JTable table) {
        JPanel p = new JPanel(new java.awt.BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(Ui.heading(caption), java.awt.BorderLayout.NORTH);
        p.add(Ui.scroll(table), java.awt.BorderLayout.CENTER);
        return p;
    }

    public static void main(String[] args) {
        AdminSession.open(Bank.get().auth().signInAdmin("admin", "1234"));
        new AdminDashboard();
    }
}
