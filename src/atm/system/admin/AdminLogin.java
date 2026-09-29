package atm.system.admin;

import atm.core.Bank;
import atm.core.Log;
import atm.core.dao.AdminUserDao;
import atm.system.Ui;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Staff sign-in. Runs the same three-attempt lock as a card, and every failure is recorded
 * so the security screen can show who has been guessing.
 */
public class AdminLogin extends JFrame implements ActionListener {

    private JTextField userField;
    private JPasswordField pinField;
    private final JButton signIn = Ui.button("SIGN IN");
    private final JButton toCustomer = Ui.ghostButton("CUSTOMER LOGIN");
    private final JButton close = Ui.ghostButton("CLOSE");

    public AdminLogin() {
        setTitle("Back office - staff sign in");
        setSize(640, 540);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(java.awt.Color.WHITE);

        userField = Ui.textField(20);
        pinField = Ui.pinField(20);
        signIn.addActionListener(this);
        toCustomer.addActionListener(e -> {
            setVisible(false);
            dispose();
            new atm.system.Login();
        });
        close.addActionListener(e -> dispose());

        Ui.Form form = new Ui.Form("STAFF SIGN IN")
            .subtitle("Back office access. Three wrong PINs lock the account.")
            .row("Staff ID", userField)
            .row("PIN", pinField)
            .note("Default account: admin / 1234")
            .buttons(signIn, toCustomer, close);

        setContentPane(form.panel());
        getRootPane().setDefaultButton(signIn);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Ui.run(this, () -> {
            AdminUserDao.Admin admin = Bank.get().auth()
                .signInAdmin(userField.getText(), new String(pinField.getPassword()));
            AdminSession.open(admin);
            Log.info("Staff " + admin.username() + " signed in to the back office");
            pinField.setText("");
            setVisible(false);
            dispose();
            new AdminDashboard().setVisible(true);
        });
    }

    public static void main(String[] args) {
        new AdminLogin();
    }
}
