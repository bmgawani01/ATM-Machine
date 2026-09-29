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
 * Cash deposit. The amount is parsed and credited by {@code atm.core.MoneyService}, which
 * updates the balance with a guarded UPDATE and records the movement against the session's
 * account, so the amount can no longer be typed straight into SQL.
 */
public class Deposit extends JFrame implements ActionListener {

    JTextField t1, t2;
    JButton b1, b2, b3;
    JLabel l1, l2, l3;
    String pin;

    Deposit(String pin) {
        this.pin = pin;
        Bank.CustomerSession session = AppSession.require(pin);

        t1 = Ui.textField(20);
        t1.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 20));
        t1.setToolTipText("Whole dollars only, for example 250");

        JButton deposit = Ui.button("DEPOSIT");
        deposit.addActionListener(this);
        b1 = deposit;
        b2 = Ui.backButton(this, pin);
        b3 = Ui.cancelButton(this);

        Ui.Form form = new Ui.Form("ENTER AMOUNT YOU WANT TO DEPOSIT")
            .subtitle("Cash is credited to the account on your card.")
            .row("Amount (USD)", t1)
            .note("Available balance: " + Money.usd(Bank.get().balance(session)))
            .buttons(b1, b2, b3);

        Ui.shell(this, "ATM - Deposit", form.panel(), 900, 820);
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
                    "Enter the amount you want to deposit");
            }
            MovementResult result = Bank.get().deposit(session, amount,
                "Cash deposit at the machine", Ids.idempotencyKey());
            Ui.receipt(this, "Deposit accepted", result.txn().reference(),
                result.txn().amount(), result.balance(),
                result.duplicate() ? "This deposit had already been recorded."
                    : "Notes: " + result.txn().note());
            setVisible(false);
            dispose();
            new Transactions(pin).setVisible(true);
        });
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new Deposit(AppSession.pin());
    }
}
