package atm.system;

import com.toedter.calendar.JDateChooser;
import atm.core.Log;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/**
 * Signup page 1: personal details.
 *
 * <p>Nothing is written here. The details are staged in {@link SignupForm} under a form
 * number and the account is opened once, on page 3, so a customer who abandons the form
 * leaves no half-finished customer in the database.
 */
public class Signup extends JFrame implements ActionListener {

    JLabel l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15;
    JTextField t1, t2, t3, t4, t5, t6, t7;
    JRadioButton r1, r2, r3, r4, r5;
    JButton b;
    JDateChooser dateChooser;

    private final SignupForm form;
    private boolean handedOff;

    public Signup() {
        this(new SignupForm(null));
    }

    /**
     * Reopens page 1 on an application that is already in progress, so BACK from page 2 shows
     * what the customer already typed instead of starting a blank form.
     */
    public Signup(SignupForm staged) {
        this.form = staged;

        setTitle("New account application - page 1 of 3");
        setSize(880, 700);
        setLocationRelativeTo(null);
        getContentPane().setBackground(java.awt.Color.WHITE);

        t1 = Ui.textField(26);
        t2 = Ui.textField(26);
        t3 = Ui.textField(26);
        t4 = Ui.textField(26);
        t5 = Ui.textField(26);

        dateChooser = new JDateChooser();
        dateChooser.setDateFormatString("dd/MM/yyyy");
        dateChooser.setPreferredSize(new java.awt.Dimension(240, 34));

        r1 = new JRadioButton("Male", true);
        r2 = new JRadioButton("Female");
        ButtonGroup genderGroup = new ButtonGroup();
        genderGroup.add(r1);
        genderGroup.add(r2);

        r3 = new JRadioButton("Married");
        r4 = new JRadioButton("Unmarried", true);
        r5 = new JRadioButton("Other");
        ButtonGroup maritalGroup = new ButtonGroup();
        maritalGroup.add(r3);
        maritalGroup.add(r4);
        maritalGroup.add(r5);

        restore(staged);

        JPanel genders = new JPanel(new GridLayout(1, 2, 0, 0));
        genders.setOpaque(false);
        r1.setBackground(java.awt.Color.WHITE);
        r2.setBackground(java.awt.Color.WHITE);
        genders.add(r1);
        genders.add(r2);

        JPanel marital = new JPanel(new GridLayout(1, 3, 0, 0));
        marital.setOpaque(false);
        for (JRadioButton r : new JRadioButton[] {r3, r4, r5}) {
            r.setBackground(java.awt.Color.WHITE);
            marital.add(r);
        }

        b = Ui.button("NEXT");
        b.addActionListener(this);
        JButton cancel = Ui.cancelButton(this, () -> SignupForm.discard(form.formno));
        Ui.onAbandonedClose(this, () -> handedOff, () -> SignupForm.discard(form.formno));

        Ui.Form formPanel = new Ui.Form("APPLICATION " + this.form.formno)
            .subtitle("Page 1 of 3: personal details. Fields marked with * are required.")
            .row("Full name *", t1)
            .row("Father's / guardian's name", t2)
            .row("Date of birth", dateChooser)
            .row("Gender", genders)
            .row("Email address", t3)
            .row("Marital status", marital)
            .row("Address", t4)
            .row("City", t5)
            .buttons(b, cancel);

        setLayout(new BorderLayout());
        getContentPane().add(formPanel.panel(), BorderLayout.CENTER);
        getRootPane().setDefaultButton(b);
        setVisible(true);
    }

    /** Puts back what a previous visit to this page had already captured. */
    private void restore(SignupForm staged) {
        t1.setText(staged.name);
        t2.setText(staged.fname);
        t3.setText(staged.email);
        t4.setText(staged.address);
        t5.setText(staged.city);

        if (!staged.dob.isEmpty()) {
            try {
                dateChooser.setDate(new java.text.SimpleDateFormat("dd/MM/yyyy")
                    .parse(staged.dob));
            } catch (java.text.ParseException e) {
                Log.warn("Ignoring unreadable staged date of birth: " + staged.dob);
            }
        }

        r1.setSelected("Male".equals(staged.gender));
        r2.setSelected(!"Male".equals(staged.gender));
        r3.setSelected("Married".equals(staged.marital));
        r4.setSelected(!"Married".equals(staged.marital) && !"Other".equals(staged.marital));
        r5.setSelected("Other".equals(staged.marital));
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() != b) {
            return;
        }
        Ui.run(this, () -> {
            form.name = t1.getText().trim();
            if (form.name.isEmpty()) {
                throw new atm.core.AtmException(atm.core.AtmException.Reason.VALIDATION,
                    "Name is required");
            }
            form.fname = t2.getText().trim();
            if (dateChooser.getDate() != null) {
                form.dob = new java.text.SimpleDateFormat("dd/MM/yyyy")
                    .format(dateChooser.getDate());
            }
            form.gender = r1.isSelected() ? "Male" : "Female";
            form.email = t3.getText().trim();
            form.marital = r3.isSelected() ? "Married" : r4.isSelected() ? "Unmarried" : "Other";
            form.address = t4.getText().trim();
            form.city = t5.getText().trim();

            handedOff = true;
            setVisible(false);
            dispose();
            new Signup2(form.formno).setVisible(true);
        });
    }

    public static void main(String[] args) {
        new Signup();
    }
}
