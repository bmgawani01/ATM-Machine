package atm.system.admin;

import atm.core.AtmException;
import atm.core.Bank;
import atm.core.Money;
import atm.system.Ui;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The frame every back-office screen sits in: a title bar naming the signed-in staff member,
 * a row of navigation buttons, and a content area the subclass fills in.
 *
 * <p>Subclasses implement {@link #build(JPanel)} and get sign-in checks, session-timeout
 * handling and navigation for free.
 */
public abstract class AdminShell extends JFrame {

    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel status = Ui.hint("");

    protected AdminShell(String title) {
        super(title);
        AdminSession.require();
        setSize(1180, 780);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Color.WHITE);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Color.WHITE);
        root.add(header(), BorderLayout.NORTH);
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        root.add(content, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(6, 18, 8, 18));
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel header() {
        var admin = AdminSession.require();

        JPanel top = new JPanel(new BorderLayout(10, 8));
        top.setBackground(Ui.INK);
        top.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));

        JPanel titles = new JPanel(new BorderLayout());
        titles.setOpaque(false);
        JLabel t = new JLabel(getTitle());
        t.setForeground(Color.WHITE);
        t.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 18));
        JLabel who = new JLabel("Staff " + admin.fullName() + " (" + admin.username()
            + ", " + admin.role() + ")");
        who.setForeground(new Color(0xC9, 0xD1, 0xD9));
        titles.add(t, BorderLayout.NORTH);
        titles.add(who, BorderLayout.SOUTH);
        top.add(titles, BorderLayout.NORTH);

        JPanel nav = new JPanel(new GridLayout(1, 9, 6, 0));
        nav.setOpaque(false);
        nav.add(navButton("Dashboard", AdminDashboard::new));
        nav.add(navButton("Customers", AdminCustomers::new));
        nav.add(navButton("Accounts", AdminAccounts::new));
        nav.add(navButton("Transactions", AdminTransactions::new));
        nav.add(navButton("Cards", AdminCards::new));
        nav.add(navButton("Cash", AdminCash::new));
        nav.add(navButton("Security", AdminSecurity::new));
        nav.add(navButton("Reports", AdminReports::new));
        JButton out = Ui.ghostButton("Sign out");
        out.setForeground(Color.WHITE);
        out.setBackground(new Color(0x3A, 0x3A, 0x3A));
        out.addActionListener(e -> {
            Ui.run(AdminShell.this, () -> {
                if (Ui.confirm(AdminShell.this, "Sign out of the back office?")) {
                    AdminSession.clear();
                    setVisible(false);
                    dispose();
                    new atm.system.Login();
                }
            });
        });
        nav.add(out);
        top.add(nav, BorderLayout.CENTER);
        return top;
    }

    private JButton navButton(String text, java.util.function.Supplier<JFrame> target) {
        JButton b = Ui.ghostButton(text);
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(0x3A, 0x3A, 0x3A));
        b.addActionListener((ActionEvent e) -> open(target.get()));
        return b;
    }

    private void open(JFrame next) {
        try {
            setVisible(false);
            dispose();
            next.setVisible(true);
        } catch (AtmException ex) {
            Ui.run(this, () -> {
                throw ex;
            });
        }
    }

    /** Fills in the screen's own content. */
    protected abstract void build(JPanel area);

    protected final JPanel content() {
        return content;
    }

    protected final void status(String text) {
        status.setText(text);
    }

    /** Reads a field as a trimmed string. */
    protected static String text(javax.swing.text.JTextComponent field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    protected static String money(java.math.BigDecimal amount) {
        return Money.usd(amount);
    }

    /** A timestamp for a table cell: "2026-09-29 14:03:11". */
    protected static String stamp(java.time.LocalDateTime when) {
        return when == null ? "-" : when.toString().replace('T', ' ');
    }
}
