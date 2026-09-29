package atm.system;

import atm.core.Bank;
import atm.core.model.Customer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextField;

/**
 * The customer's own details. Read-only identity fields are shown as they are on record, and
 * the contact fields the customer is allowed to correct are editable and saved through
 * {@code atm.core.CardService}, which writes an audit entry naming the change.
 */
public class Profile extends JFrame implements ActionListener {

    private final Bank.CustomerSession session;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField addressField;
    private JTextField cityField;

    Profile(String pin) {
        this.session = AppSession.require(pin);
        Customer c = Bank.get().profile(session);

        emailField = Ui.textField(24);
        emailField.setText(c.email());
        phoneField = Ui.textField(24);
        phoneField.setText(c.phone());
        addressField = Ui.textField(24);
        addressField.setText(Ui.orDash(c.address()));
        cityField = Ui.textField(24);
        cityField.setText(Ui.orDash(c.city()));

        JButton save = Ui.button("SAVE CHANGES");
        save.addActionListener(this);

        Ui.Form form = new Ui.Form("MY PROFILE")
            .subtitle("Signed in as " + session.name())
            .row("Card number", Ui.readOnly(c.cardNo()))
            .row("Account number", Ui.readOnly(session.accountNo()))
            .row("Date of birth", Ui.readOnly(Ui.orDash(c.dob())))
            .row("Account type", Ui.readOnly(c.accountType()))
            .row("Status", Ui.readOnly(c.status()))
            .section("Contact details you can update")
            .row("Email", emailField)
            .row("Phone", phoneField)
            .row("Address", addressField)
            .row("City", cityField)
            .note("Name, date of birth and identity numbers can only be changed at the branch.")
            .buttons(save, Ui.backButton(this, pin), Ui.cancelButton(this));

        Ui.shell(this, "ATM - My profile", form.panel(), 900, 1000);
        getRootPane().setDefaultButton(save);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Ui.run(this, () -> {
            Bank.get().updateProfile(session, emailField.getText().trim(),
                phoneField.getText().trim(), addressField.getText().trim(),
                cityField.getText().trim());
            Ui.info(this, "Your details have been saved.");
        });
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new Profile(AppSession.pin());
    }
}
