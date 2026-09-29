package atm.system;

import atm.core.AtmException;
import atm.core.Bank;
import atm.core.service.OtpService;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPasswordField;

/**
 * PIN change. The new PIN is hashed by {@code atm.core.CardService}, the current PIN is
 * re-checked, a verification code is required, and every existing session for the card is
 * revoked. That last part is why this screen returns to sign-in on success instead of going
 * back to the menu: the session the menu would use no longer exists.
 */
public class Pin extends JFrame implements ActionListener {

    JPasswordField t1, t2;
    JButton b1, b2;
    JLabel l1, l2, l3;
    String pin;
    private JPasswordField currentPin;

    Pin(String pin) {
        this.pin = pin;
        AppSession.require(pin);

        JPasswordField current = Ui.pinField(20);
        JPasswordField fresh = Ui.pinField(20);
        JPasswordField confirm = Ui.pinField(20);
        this.currentPin = current;
        t1 = fresh;
        t2 = confirm;

        b1 = Ui.button("CHANGE PIN");
        b1.addActionListener(this);
        b2 = Ui.backButton(this, pin);

        Ui.Form form = new Ui.Form("CHANGE YOUR PIN")
            .subtitle("Four to six digits. Changing it signs you out everywhere.")
            .row("Current PIN", current)
            .row("New PIN", fresh)
            .row("Confirm new PIN", confirm)
            .buttons(b1, b2);

        Ui.shell(this, "ATM - PIN change", form.panel(), 900, 860);
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
            String current = new String(currentPin.getPassword());
            String fresh = new String(t1.getPassword());
            String confirm = new String(t2.getPassword());

            if (fresh.isBlank()) {
                throw new AtmException(AtmException.Reason.VALIDATION, "Enter a new PIN");
            }
            if (!fresh.equals(confirm)) {
                throw new AtmException(AtmException.Reason.VALIDATION,
                    "The two new PINs do not match");
            }
            if (fresh.equals(current)) {
                throw new AtmException(AtmException.Reason.VALIDATION,
                    "The new PIN must be different from the current one");
            }

            String code = OtpPrompt.ask(this, session, OtpService.PURPOSE_PIN_CHANGE);
            if (code == null) {
                Ui.info(this, "Cancelled. Your PIN was not changed.");
                return;
            }

            Bank.get().changePin(session, current, fresh, code);
            setVisible(false);
            dispose();
            AppSession.clear();
            Ui.info(null, "Your PIN has been changed and every session was signed out.\n"
                + "Please sign in again with your new PIN.");
            new Login();
        });
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new Pin(AppSession.pin());
    }
}
