package atm.system;

import atm.core.Bank;
import atm.core.Ids;
import atm.core.Money;
import atm.core.model.MovementResult;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * Cash withdrawal. The screen only collects the amount; the per-transaction limit, the daily
 * limit, the balance, the note denominations the machine actually holds and the audit entry
 * are all handled by {@code atm.core.MoneyService}.
 */
public class Withdrawl extends JFrame implements ActionListener {

    JTextField t1, t2;
    JButton b1, b2, b3;
    JLabel l1, l2, l3, l4;
    String pin;

    Withdrawl(String pin) {
        this.pin = pin;
        Bank.CustomerSession session = AppSession.require(pin);

        t1 = Ui.textField(20);
        t1.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 20));
        t1.setToolTipText("Whole dollars only, for example 200");

        b1 = Ui.button("WITHDRAW");
        b1.addActionListener(this);
        b2 = Ui.backButton(this, pin);
        b3 = Ui.cancelButton(this);

        l4 = Ui.hint("");

        Ui.Form form = new Ui.Form("ENTER AMOUNT YOU WANT TO WITHDRAW")
            .subtitle("Cash is paid from the machine and the notes are taken off the float.")
            .row("Amount (USD)", t1)
            .note("Available: " + Money.usd(Bank.get().balance(session))
                + "   |   Withdrawn today: " + Money.usd(Bank.get().spentToday(session)))
            .note(Bank.get().limitsText(session))
            .buttons(b1, b2, b3);

        Ui.shell(this, "ATM - Cash withdrawal", form.panel(), 900, 860);
        getRootPane().setDefaultButton(b1);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() != b1) {
            return;
        }
        Ui.run(this, () -> {
            Bank.CustomerSession session = AppSession.require(pin);
            String amount = t1.getText().trim();
            if (amount.isEmpty()) {
                throw new atm.core.AtmException(atm.core.AtmException.Reason.VALIDATION,
                    "Enter the amount you want to withdraw");
            }
            MovementResult result = Bank.get().withdraw(session, amount,
                "Cash withdrawal at the machine", Ids.idempotencyKey());
            Ui.receipt(this, "Please take your cash", result.txn().reference(),
                result.txn().amount(), result.balance(),
                result.duplicate() ? "This withdrawal had already been dispensed."
                    : "Notes were issued from the machine float.");
            setVisible(false);
            dispose();
            new Transactions(pin).setVisible(true);
        });
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new Withdrawl(AppSession.pin());
    }
}
