package atm.system;

import atm.core.AtmException;
import atm.core.Bank;
import atm.core.Ids;
import atm.core.Money;
import atm.core.model.Biller;
import atm.core.model.MovementResult;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * Bill payment. The biller list is public reference data read through {@code Bank.billers()},
 * and the payment itself is recorded by {@code atm.core.MoneyService} against the session's
 * account with the day's bill-payment limit applied.
 */
public class BillPayment extends JFrame implements ActionListener {

    private final Bank.CustomerSession session;
    private final List<Biller> billers = new ArrayList<>();
    private JComboBox<String> billerBox;
    private JTextField refField;
    private JTextField amountField;
    private Ui.Form form;

    BillPayment(String pin) {
        this.session = AppSession.require(pin);

        billers.addAll(Bank.get().billers());
        String[] names = new String[billers.size()];
        for (int i = 0; i < billers.size(); i++) {
            Biller b = billers.get(i);
            names[i] = b.name() + "  -  " + b.category();
        }
        if (names.length == 0) {
            names = new String[] {"No billers configured"};
        }
        billerBox = Ui.combo(names);
        billerBox.addActionListener(e -> updateRefHint());

        refField = Ui.textField(20);
        amountField = Ui.textField(20);

        JButton pay = Ui.button("PAY BILL");
        pay.addActionListener(this);

        form = new Ui.Form("PAY A BILL")
            .subtitle("Available: " + Money.usd(Bank.get().balance(session)))
            .row("Biller", billerBox)
            .row("Account / meter no.", refField)
            .row("Amount (USD)", amountField)
            .note("")
            .buttons(pay, Ui.backButton(this, pin), Ui.cancelButton(this));

        Ui.shell(this, "ATM - Bill payment", form.panel(), 900, 900);
        getRootPane().setDefaultButton(pay);
        updateRefHint();
        setVisible(true);
    }

    private void updateRefHint() {
        int i = billerBox.getSelectedIndex();
        form.noteText(i >= 0 && i < billers.size()
            ? "Enter the " + billers.get(i).customerRefLabel().toLowerCase() + " on your bill."
            : "Choose a biller first.");
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Ui.run(this, () -> {
            int i = billerBox.getSelectedIndex();
            if (i < 0 || i >= billers.size()) {
                throw new AtmException(AtmException.Reason.VALIDATION, "Choose a biller");
            }
            String ref = refField.getText().trim();
            String amount = amountField.getText().trim();
            if (ref.isEmpty()) {
                throw new AtmException(AtmException.Reason.VALIDATION,
                    "Enter the " + billers.get(i).customerRefLabel().toLowerCase());
            }
            if (amount.isEmpty()) {
                throw new AtmException(AtmException.Reason.VALIDATION, "Enter an amount");
            }
            Biller biller = billers.get(i);
            MovementResult result = Bank.get().payBill(session, biller.id(), ref, amount,
                Ids.idempotencyKey());
            Ui.receipt(this, "Bill paid", result.txn().reference(), result.txn().amount(),
                result.balance(), biller.name() + " ref " + ref
                    + (result.duplicate() ? " (already processed)" : ""));
            refField.setText("");
            amountField.setText("");
        });
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new BillPayment(AppSession.pin());
    }
}
