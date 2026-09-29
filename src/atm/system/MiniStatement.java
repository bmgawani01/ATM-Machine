package atm.system;

import atm.core.Bank;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * The last few movements on the account, formatted by {@code atm.core.StatementService} and
 * printed on the machine.
 */
public class MiniStatement extends JFrame implements ActionListener {

    JButton b1, b2;
    JLabel l1;
    String pin;
    private JTextArea body;

    MiniStatement(String pin) {
        this.pin = pin;
        Bank.CustomerSession session = AppSession.require(pin);

        setLayout(null);
        getContentPane().setBackground(java.awt.Color.WHITE);
        setSize(560, 720);
        setLocationRelativeTo(null);

        l1 = Ui.title("Mini statement");
        Ui.at(l1, 30, 24, 500, 32);
        add(l1);

        JLabel who = Ui.hint(session.name() + "   |   "
            + atm.core.Ids.maskAccount(session.accountNo()));
        Ui.at(who, 30, 56, 500, 20);
        add(who);

        body = Ui.textArea(20, 44);
        fill(session);
        JScrollPane scroller = Ui.scroll(body);
        Ui.at(scroller, 30, 90, 500, 470);
        add(scroller);

        b1 = Ui.backButton(this, pin);
        Ui.at(b1, 30, 580, 150, 38);
        add(b1);

        b2 = Ui.ghostButton("REFRESH");
        b2.addActionListener(this);
        Ui.at(b2, 200, 580, 150, 38);
        add(b2);

        setVisible(true);
    }

    private void fill(Bank.CustomerSession session) {
        List<String> lines = Bank.get().miniStatement(session, 10);
        body.setText(lines.isEmpty() ? "No transactions yet." : String.join("\n", lines));
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Ui.run(this, () -> fill(AppSession.require(pin)));
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new MiniStatement(AppSession.pin());
    }
}
