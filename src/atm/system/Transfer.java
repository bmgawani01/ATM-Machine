package atm.system;

import atm.core.AtmException;
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
 * Transfer to another account.
 *
 * <p>The customer types the destination account number. It is resolved and locked on the
 * server, so nothing about the destination can be spoofed from the screen, and the customer
 * is not shown a list of everyone else's accounts. A transfer above the account's OTP
 * threshold needs a verification code; the threshold itself is enforced inside
 * {@code atm.core.MoneyService} rather than trusted from this screen.
 */
public class Transfer extends JFrame implements ActionListener {

    private final Bank.CustomerSession session;
    private JTextField toField;
    private JTextField amountField;
    private JTextField noteField;
    private JLabel thresholdHint;

    Transfer(String pin) {
        this.session = AppSession.require(pin);

        toField = Ui.textField(20);
        toField.setToolTipText("The destination account number");
        amountField = Ui.textField(20);
        amountField.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 20));
        noteField = Ui.textField(20);
        thresholdHint = Ui.hint("");

        JButton send = Ui.button("TRANSFER");
        send.addActionListener(this);

        atm.core.model.Limits limits = Bank.get().limits(session);
        thresholdHint.setText("A transfer above " + Money.usd(limits.requireOtpAbove())
            + " asks for a verification code. Per transaction limit "
            + Money.usd(limits.perTxn()) + ".");

        Ui.Form form = new Ui.Form("TRANSFER FUNDS")
            .subtitle("Your account " + Ids.maskAccount(session.accountNo()))
            .row("To account no.", toField)
            .row("Amount (USD)", amountField)
            .row("Note", noteField)
            .note("Available: " + Money.usd(Bank.get().balance(session)))
            .note(thresholdHint.getText())
            .buttons(send, Ui.backButton(this, pin), Ui.cancelButton(this));

        Ui.shell(this, "ATM - Transfer", form.panel(), 900, 900);
        getRootPane().setDefaultButton(send);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Ui.run(this, () -> {
            String to = toField.getText().trim();
            String amount = amountField.getText().trim();
            String note = noteField.getText().trim();

            if (to.isEmpty()) {
                throw new AtmException(AtmException.Reason.VALIDATION,
                    "Enter the destination account number");
            }
            if (amount.isEmpty()) {
                throw new AtmException(AtmException.Reason.VALIDATION, "Enter an amount");
            }
            if (to.equals(session.accountNo())) {
                throw new AtmException(AtmException.Reason.VALIDATION,
                    "That is your own account");
            }

            boolean needsOtp = Bank.get().transferNeedsOtp(session, amount);
            String code = needsOtp ? OtpPrompt.ask(this, session,
                atm.core.service.OtpService.PURPOSE_TRANSFER) : null;
            if (needsOtp && code == null) {
                Ui.info(this, "Cancelled. No money was sent.");
                return;
            }

            MovementResult result = Bank.get().transfer(session, to, amount,
                note.isEmpty() ? "Transfer at the machine" : note,
                Ids.idempotencyKey(), code);

            Ui.receipt(this, "Transfer sent", result.txn().reference(),
                result.txn().amount(), result.balance(),
                "To " + Ids.maskAccount(to)
                    + (result.duplicate() ? " (already processed)" : ""));
            setVisible(false);
            dispose();
            new Transactions(AppSession.pin()).setVisible(true);
        });
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new Transfer(AppSession.pin());
    }
}
