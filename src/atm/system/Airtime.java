package atm.system;

import atm.core.AtmException;
import atm.core.Bank;
import atm.core.Ids;
import atm.core.Money;
import atm.core.model.MovementResult;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JTextField;

/**
 * Airtime top-up. The network, the phone number and the amount are validated by
 * {@code atm.core.MoneyService}, which also enforces the per-transaction limit and writes
 * both the money movement and the airtime record.
 */
public class Airtime extends JFrame implements ActionListener {

    private static final String[] NETWORKS = {"MTN", "Airtel", "Telecom", "Africell", "Glo"};

    private final Bank.CustomerSession session;
    private JComboBox<String> networkBox;
    private JTextField phoneField;
    private JTextField amountField;

    Airtime(String pin) {
        this.session = AppSession.require(pin);

        networkBox = Ui.combo(NETWORKS);
        phoneField = Ui.textField(20);
        amountField = Ui.textField(20);

        JButton buy = Ui.button("BUY AIRTIME");
        buy.addActionListener(this);

        Ui.Form form = new Ui.Form("BUY AIRTIME")
            .subtitle("Available: " + Money.usd(Bank.get().balance(session)))
            .row("Network", networkBox)
            .row("Phone number", phoneField)
            .row("Amount (USD)", amountField)
            .note("Common amounts: 1, 2, 5, 10, 20")
            .buttons(buy, Ui.backButton(this, pin), Ui.cancelButton(this));

        Ui.shell(this, "ATM - Airtime", form.panel(), 900, 880);
        getRootPane().setDefaultButton(buy);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Ui.run(this, () -> {
            String network = (String) networkBox.getSelectedItem();
            String phone = phoneField.getText().trim();
            String amount = amountField.getText().trim();
            if (phone.isEmpty()) {
                throw new AtmException(AtmException.Reason.VALIDATION,
                    "Enter the phone number to top up");
            }
            if (amount.isEmpty()) {
                throw new AtmException(AtmException.Reason.VALIDATION, "Enter an amount");
            }
            MovementResult result = Bank.get().buyAirtime(session, network, phone, amount,
                Ids.idempotencyKey());
            Ui.receipt(this, "Airtime sent", result.txn().reference(), result.txn().amount(),
                result.balance(), network + " to " + phone
                    + (result.duplicate() ? " (already processed)" : ""));
            phoneField.setText("");
            amountField.setText("");
        });
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new Airtime(AppSession.pin());
    }
}
