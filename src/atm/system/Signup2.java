package atm.system;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/**
 * Signup page 2: additional details, staged into the same {@link SignupForm} as page 1.
 */
public class Signup2 extends JFrame implements ActionListener {

    JLabel l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13;
    JButton b;
    JRadioButton r1, r2, r3, r4;
    JTextField t1, t2, t3;
    JComboBox<String> c1, c2, c3, c4, c5;
    String formno;

    private final SignupForm form;
    private boolean handedOff;

    public Signup2(String formno) {
        this.formno = formno;
        this.form = SignupForm.of(formno);
        if (form == null) {
            throw new IllegalStateException(
                "No staged application for " + formno + ". Start again from the sign-up screen.");
        }

        setTitle("New account application - page 2 of 3");
        setSize(880, 760);
        setLocationRelativeTo(null);
        getContentPane().setBackground(java.awt.Color.WHITE);

        t1 = Ui.textField(24);
        t2 = Ui.textField(24);
        t3 = Ui.textField(24);
        t1.setToolTipText("Optional");
        t2.setToolTipText("6 to 20 digits");
        t3.setToolTipText("9 to 15 digits, used for the verification code");

        c1 = Ui.combo(new String[] {"Hindu", "Muslim", "Christian", "Sikh", "Buddhist", "Jain",
            "Other"});
        c2 = Ui.combo(new String[] {"General", "OBC", "SC", "ST"});
        c3 = Ui.combo(new String[] {"Below 1,00,000", "1,00,000 - 5,00,000",
            "5,00,000 - 10,00,000", "Above 10,00,000"});
        c4 = Ui.combo(new String[] {"Metric", "High School", "Graduate", "Post Graduate",
            "Doctorate"});
        c5 = Ui.combo(new String[] {"Salaried", "Business", "Professional", "Student",
            "Retired", "Other"});

        r1 = new JRadioButton("Yes", true);
        r2 = new JRadioButton("No");
        ButtonGroup senior = new ButtonGroup();
        senior.add(r1);
        senior.add(r2);

        r3 = new JRadioButton("Yes");
        r4 = new JRadioButton("No", true);
        ButtonGroup existing = new ButtonGroup();
        existing.add(r3);
        existing.add(r4);

        JPanel seniorRow = radioRow(r1, r2);
        JPanel existingRow = radioRow(r3, r4);

        b = Ui.button("NEXT");
        b.addActionListener(this);
        JButton back = Ui.ghostButton("BACK");
        back.addActionListener(e -> {
            handedOff = true;
            setVisible(false);
            dispose();
            new Signup(SignupForm.of(formno)).setVisible(true);
        });

        Ui.Form panel = new Ui.Form("APPLICATION " + formno)
            .subtitle("Page 2 of 3: additional details")
            .row("Religion", c1)
            .row("Category", c2)
            .row("Income", c3)
            .row("Educational qualification", c4)
            .row("Occupation", c5)
            .row("PAN number", t1)
            .row("Aadhar / national ID *", t2)
            .row("Senior citizen", seniorRow)
            .row("Existing account here", existingRow)
            .row("Phone *", t3)
            .buttons(b, back, Ui.cancelButton(this, () -> SignupForm.discard(formno)));

        Ui.onAbandonedClose(this, () -> handedOff, () -> SignupForm.discard(formno));

        setLayout(new BorderLayout());
        getContentPane().add(panel.panel(), BorderLayout.CENTER);
        getRootPane().setDefaultButton(b);
        setVisible(true);
    }

    private static JPanel radioRow(JRadioButton... buttons) {
        JPanel p = new JPanel(new GridLayout(1, buttons.length, 0, 0));
        p.setOpaque(false);
        for (JRadioButton b : buttons) {
            b.setBackground(java.awt.Color.WHITE);
            p.add(b);
        }
        return p;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() != b) {
            return;
        }
        Ui.run(this, () -> {
            form.religion = (String) c1.getSelectedItem();
            form.category = (String) c2.getSelectedItem();
            form.income = (String) c3.getSelectedItem();
            form.education = (String) c4.getSelectedItem();
            form.occupation = (String) c5.getSelectedItem();
            form.pan = t1.getText().trim();
            form.aadhar = t2.getText().trim();
            form.seniorCitizen = r1.isSelected() ? "Yes" : "No";
            form.existingAccount = r3.isSelected() ? "Yes" : "No";
            form.phone = t3.getText().trim();

            handedOff = true;
            setVisible(false);
            dispose();
            new Signup3(formno).setVisible(true);
        });
    }

    public static void main(String[] args) {
        new Signup2(new SignupForm(null).formno);
    }
}
