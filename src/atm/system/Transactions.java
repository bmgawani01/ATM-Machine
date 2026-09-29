package atm.system;

import atm.core.Bank;
import atm.core.Money;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The transaction menu. Every button now opens a real screen instead of a TODO, and EXIT
 * signs out properly so the session is revoked on the server rather than leaked.
 */
public class Transactions extends JFrame implements ActionListener {

    JLabel l1;
    JButton b1, b2, b3, b4, b5, b6, b7;
    JButton b8, b9, b10, b11, b12, b13;
    String pin;

    public Transactions(String pin) {
        this.pin = pin;
        Bank.CustomerSession session = AppSession.require(pin);

        setTitle("ATM - Select your transaction");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(900, 860);
        setLocationRelativeTo(null);
        setLayout(null);

        JLabel background = Ui.background("/icons/atm.jpg", 900, 820);
        add(background);

        l1 = new JLabel("Please Select Your Transaction");
        l1.setForeground(java.awt.Color.WHITE);
        l1.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 20));
        Ui.at(l1, 30, 20, 840, 32);
        background.add(l1);

        JLabel who = new JLabel("Welcome, " + session.name() + "   |   "
            + atm.core.Ids.maskCard(session.cardNo()) + "   |   "
            + Money.usd(Bank.get().balance(session)) + " available");
        who.setForeground(new java.awt.Color(0xE6, 0xE6, 0xE6));
        who.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 14));
        Ui.at(who, 30, 52, 840, 24);
        background.add(who);

        JPanel grid = new JPanel(new GridLayout(7, 2, 14, 12));
        grid.setOpaque(false);
        Ui.at(grid, 40, 95, 820, 470);
        background.add(grid);

        b1 = menuButton("DEPOSIT");
        b2 = menuButton("CASH WITHDRAWL");
        b3 = menuButton("FAST CASH");
        b4 = menuButton("MINI STATEMENT");
        b5 = menuButton("PIN CHANGE");
        b6 = menuButton("BALANCE ENQUIRY");
        b7 = menuButton("EXIT");
        b8 = menuButton("TRANSFER");
        b9 = menuButton("HISTORY");
        b10 = menuButton("CARD SERVICES");
        b11 = menuButton("AIRTIME");
        b12 = menuButton("BILL PAYMENTS");
        b13 = menuButton("MY PROFILE");

        for (JButton b : new JButton[] {b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13}) {
            grid.add(b);
            b.addActionListener(this);
        }
        grid.add(new JLabel());

        setVisible(true);
    }

    private JButton menuButton(String text) {
        JButton b = Ui.button(text);
        b.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 14));
        b.setBackground(new java.awt.Color(0xF7, 0xF7, 0xF7));
        b.setForeground(Ui.INK);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new java.awt.Color(0x2A, 0x2A, 0x2A)),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        return b;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Object src = ae.getSource();
        if (src == b7) {
            Ui.run(this, () -> {
                if (Ui.confirm(this, "Sign out of this session?")) {
                    AppSession.signOut();
                    setVisible(false);
                    dispose();
                    new Login();
                }
            });
            return;
        }
        Ui.run(this, () -> {
            setVisible(false);
            if (src == b1) {
                new Deposit(pin).setVisible(true);
            } else if (src == b2) {
                new Withdrawl(pin).setVisible(true);
            } else if (src == b3) {
                new FastCash(pin).setVisible(true);
            } else if (src == b4) {
                new MiniStatement(pin).setVisible(true);
            } else if (src == b5) {
                new Pin(pin).setVisible(true);
            } else if (src == b6) {
                new BalanceEnquiry(pin).setVisible(true);
            } else if (src == b8) {
                new Transfer(pin).setVisible(true);
            } else if (src == b9) {
                new History(pin).setVisible(true);
            } else if (src == b10) {
                new CardServices(pin).setVisible(true);
            } else if (src == b11) {
                new Airtime(pin).setVisible(true);
            } else if (src == b12) {
                new BillPayment(pin).setVisible(true);
            } else if (src == b13) {
                new Profile(pin).setVisible(true);
            } else {
                setVisible(true);
            }
        });
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new Transactions(AppSession.pin());
    }
}
