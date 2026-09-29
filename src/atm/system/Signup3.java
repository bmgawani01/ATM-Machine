package atm.system;

import atm.core.Bank;
import atm.core.Money;
import atm.core.model.AccountType;
import atm.core.service.OnboardingService;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/**
 * Signup page 3: services, account type and the opening deposit.
 *
 * <p>This is where the account actually happens. {@code atm.core.OnboardingService} creates
 * the customer, the account, the card and the limits in one transaction, so a half-created
 * customer is not possible, and it returns the card number and PIN that must be written down
 * for the customer.
 */
public class Signup3 extends JFrame implements ActionListener {

    JLabel l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12;
    JRadioButton r1, r2, r3, r4;
    JButton b1, b2;
    String formno;

    private final SignupForm form;
    private boolean handedOff;
    private JComboBox<String> accountTypeBox;
    private JTextField openingField;

    public Signup3(String formno) {
        this.formno = formno;
        this.form = SignupForm.of(formno);
        if (form == null) {
            throw new IllegalStateException(
                "No staged application for " + formno + ". Start again from the sign-up screen.");
        }

        setTitle("New account application - page 3 of 3");
        setSize(880, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(java.awt.Color.WHITE);

        String[] types = new String[AccountType.values().length];
        for (int i = 0; i < types.length; i++) {
            types[i] = AccountType.values()[i].label();
        }
        accountTypeBox = Ui.combo(types);
        accountTypeBox.setSelectedItem("Saving Account");

        openingField = Ui.textField(20);
        openingField.setText("1000");
        openingField.setToolTipText("Whole dollars");

        r1 = new JRadioButton("ATM Card", true);
        r2 = new JRadioButton("Debit Card");
        r3 = new JRadioButton("Internet Banking", true);
        r4 = new JRadioButton("Mobile Banking", true);
        JPanel services = new JPanel(new GridLayout(1, 2, 0, 0));
        services.setOpaque(false);
        ButtonGroup serviceGroup = new ButtonGroup();
        serviceGroup.add(r1);
        serviceGroup.add(r2);
        for (JRadioButton rb : new JRadioButton[] {r1, r2}) {
            rb.setBackground(java.awt.Color.WHITE);
            services.add(rb);
        }
        ButtonGroup onlineGroup = new ButtonGroup();
        onlineGroup.add(r3);
        onlineGroup.add(r4);
        JPanel online = new JPanel(new GridLayout(1, 2, 0, 0));
        online.setOpaque(false);
        for (JRadioButton rb : new JRadioButton[] {r3, r4}) {
            rb.setBackground(java.awt.Color.WHITE);
            online.add(rb);
        }

        b1 = Ui.button("OPEN ACCOUNT");
        b1.addActionListener(this);
        b2 = Ui.ghostButton("BACK");
        b2.addActionListener(e -> {
            handedOff = true;
            setVisible(false);
            dispose();
            new Signup2(formno).setVisible(true);
        });

        Ui.Form panel = new Ui.Form("APPLICATION " + formno)
            .subtitle("Page 3 of 3: services and opening deposit")
            .row("Name", Ui.readOnly(form.name))
            .row("Father's name", Ui.readOnly(Ui.orDash(form.fname)))
            .row("Phone", Ui.readOnly(Ui.orDash(form.phone)))
            .row("Services", services)
            .row("Online banking", online)
            .row("Account type", accountTypeBox)
            .row("Opening deposit (USD)", openingField)
            .note("Write down the card number and PIN before leaving the machine. "
                + "The PIN is shown once.")
            .buttons(b1, b2, Ui.cancelButton(this, () -> SignupForm.discard(formno)));

        Ui.onAbandonedClose(this, () -> handedOff, () -> SignupForm.discard(formno));

        setLayout(new BorderLayout());
        getContentPane().add(panel.panel(), BorderLayout.CENTER);
        getRootPane().setDefaultButton(b1);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() != b1) {
            return;
        }
        Ui.run(this, () -> {
            String chosenType = (String) accountTypeBox.getSelectedItem();
            form.acctype = chosenType;
            form.openingDeposit = openingField.getText().trim().isEmpty()
                ? BigDecimal.ZERO : new BigDecimal(openingField.getText().trim());
            form.services = "ATM Card"
                + (r2.isSelected() ? ", Debit Card" : "")
                + (r3.isSelected() ? ", Internet Banking" : "")
                + (r4.isSelected() ? ", Mobile Banking" : "");

            OnboardingService.NewAccount created =
                Bank.get().onboarding().openAccount(form.toService());

            SignupForm.discard(formno);
            handedOff = true;
            setVisible(false);
            dispose();

            Ui.info(null, "<html><body style='font-family:sans-serif;width:340px'>"
                + "<b>Your account is open</b><br><br>"
                + "Application: " + formno + "<br>"
                + "Card number: <b>" + created.cardNo() + "</b><br>"
                + "PIN: <b>" + created.pin() + "</b><br>"
                + "Account number: " + created.accountNo() + "<br>"
                + "Account type: " + created.accountType() + "<br>"
                + "Opening balance: " + Money.usd(created.openingBalance()) + "<br><br>"
                + "Write the card number and PIN down now. The PIN is not shown again."
                + "</body></html>");

            new Login();
        });
    }

    public static void main(String[] args) {
        new Signup3(new SignupForm(null).formno);
    }
}
