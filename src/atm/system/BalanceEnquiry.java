package atm.system;

import atm.core.Bank;
import atm.core.Money;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * Balance enquiry. Shows the live balance plus the limits that apply to the account, and
 * refreshes without reopening the screen.
 */
public class BalanceEnquiry extends JFrame implements ActionListener {

    JTextField t1, t2;
    JButton b1, b2, b3;
    JLabel l1, l2, l3;
    String pin;

    BalanceEnquiry(String pin) {
        this.pin = pin;
        Bank.CustomerSession session = AppSession.require(pin);

        t1 = Ui.readOnly(Money.usd(Bank.get().balance(session)));
        t1.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 22));

        b1 = Ui.button("REFRESH");
        b1.addActionListener(this);
        b2 = Ui.backButton(this, pin);
        b3 = Ui.cancelButton(this);

        Ui.Form form = new Ui.Form("BALANCE ENQUIRY")
            .subtitle("Card " + atm.core.Ids.maskCard(session.cardNo())
                + "   |   Account " + atm.core.Ids.maskAccount(session.accountNo()))
            .row("Current balance", t1)
            .note("Withdrawn today: " + Money.usd(Bank.get().spentToday(session)))
            .section("Your limits")
            .note(Bank.get().limitsText(session).replace(" | ", "\n"))
            .buttons(b1, b2, b3);

        Ui.shell(this, "ATM - Balance enquiry", form.panel(), 900, 880);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == b1) {
            Ui.run(this, () -> {
                Bank.CustomerSession session = AppSession.require(pin);
                t1.setText(Money.usd(Bank.get().balance(session)));
            });
        }
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new BalanceEnquiry(AppSession.pin());
    }
}
