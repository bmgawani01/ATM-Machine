package atm.system;

import atm.core.AtmException;
import atm.system.admin.AdminLogin;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Sign-in. The card number and PIN are checked by {@code atm.core.AuthService}, which
 * applies the three-attempt lock, so this screen only has to report what went wrong.
 */
public class Login extends JFrame implements ActionListener {

    JLabel l1, l2, l3;
    JTextField tf1;
    JPasswordField pf2;
    JButton b1, b2, b3, b4;

    public Login() {
        setTitle("AUTOMATED TELLER MACHINE");
        setSize(760, 560);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(Color.WHITE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        JPanel card = Ui.card();
        card.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 8, 6, 8);
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 2;

        JLabel logo = new JLabel(Ui.icon("/icons/logo.jpg", 84));
        g.anchor = GridBagConstraints.CENTER;
        card.add(logo, g);

        g.gridy++;
        g.insets = new Insets(14, 8, 2, 8);
        l1 = Ui.title("WELCOME TO ATM");
        g.anchor = GridBagConstraints.CENTER;
        card.add(l1, g);

        g.gridy++;
        g.insets = new Insets(16, 8, 2, 8);
        JLabel note = Ui.hint("Three wrong PINs will lock the card for a few minutes.");
        card.add(note, g);

        g.gridy++;
        g.insets = new Insets(18, 8, 4, 8);
        l2 = Ui.label("Card No:");
        card.add(l2, g);

        g.gridy++;
        g.insets = new Insets(0, 8, 4, 8);
        tf1 = Ui.textField(18);
        tf1.setPreferredSize(new Dimension(240, 38));
        card.add(tf1, g);

        g.gridy++;
        g.insets = new Insets(12, 8, 4, 8);
        l3 = Ui.label("PIN:");
        card.add(l3, g);

        g.gridy++;
        g.insets = new Insets(0, 8, 18, 8);
        pf2 = Ui.pinField(18);
        pf2.setPreferredSize(new Dimension(240, 38));
        card.add(pf2, g);

        g.gridy++;
        g.gridwidth = 2;
        g.insets = new Insets(0, 8, 10, 8);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        b1 = Ui.button("SIGN IN");
        b2 = Ui.ghostButton("CLEAR");
        b3 = Ui.ghostButton("SIGN UP");
        buttons.add(b1);
        buttons.add(b2);
        buttons.add(b3);
        card.add(buttons, g);

        g.gridy++;
        g.insets = new Insets(0, 8, 4, 8);
        b4 = Ui.ghostButton("STAFF / BACK OFFICE");
        b4.setPreferredSize(new Dimension(240, 32));
        card.add(b4, g);

        g.gridy++;
        g.insets = new Insets(12, 8, 0, 8);
        card.add(Ui.hint("Demo card 1234567890  PIN 1234   |   staff admin / 1234"), g);

        root.add(card, BorderLayout.CENTER);
        setContentPane(root);

        b1.addActionListener(this);
        b2.addActionListener(this);
        b3.addActionListener(this);
        b4.addActionListener(this);

        getRootPane().setDefaultButton(b1);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == b1) {
            signIn();
        } else if (ae.getSource() == b2) {
            tf1.setText("");
            pf2.setText("");
            tf1.requestFocusInWindow();
        } else if (ae.getSource() == b3) {
            setVisible(false);
            dispose();
            new Signup().setVisible(true);
        } else if (ae.getSource() == b4) {
            setVisible(false);
            dispose();
            new AdminLogin().setVisible(true);
        }
    }

    private void signIn() {
        Ui.run(this, () -> {
            String cardNo = tf1.getText().trim();
            String pin = new String(pf2.getPassword()).trim();
            if (cardNo.isEmpty() || pin.isEmpty()) {
                throw new AtmException(AtmException.Reason.VALIDATION,
                    "Enter your card number and PIN");
            }
            AppSession.open(cardNo, pin, "ATM");
            setVisible(false);
            dispose();
            new Transactions(pin).setVisible(true);
        });
        pf2.setText("");
    }

    public static void main(String[] args) {
        new Login();
    }
}
