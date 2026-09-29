package atm.system;

import atm.core.Bank;
import atm.core.Ids;
import atm.core.Money;
import atm.core.model.MovementResult;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Fast cash: preset amounts only, so a customer who is in a hurry never has to type. The
 * amounts are still checked against the balance, the limits and the machine's notes by
 * {@code atm.core.MoneyService}.
 */
public class FastCash extends JFrame implements ActionListener {

    private static final BigDecimal[] PRESETS = {
        BigDecimal.valueOf(100), BigDecimal.valueOf(200), BigDecimal.valueOf(500),
        BigDecimal.valueOf(1000)
    };

    JLabel l1;
    JButton b1, b2, b3, b4, b5, b6, b7;
    String pin;

    FastCash(String pin) {
        this.pin = pin;
        Bank.CustomerSession session = AppSession.require(pin);

        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
        grid.setOpaque(false);

        b1 = preset("USD " + PRESETS[0].intValue());
        b2 = preset("USD " + PRESETS[1].intValue());
        b3 = preset("USD " + PRESETS[2].intValue());
        b4 = preset("USD " + PRESETS[3].intValue());
        b1.addActionListener(this);
        b2.addActionListener(this);
        b3.addActionListener(this);
        b4.addActionListener(this);
        grid.add(b1);
        grid.add(b2);
        grid.add(b3);
        grid.add(b4);

        b5 = Ui.backButton(this, pin);
        b6 = Ui.cancelButton(this);
        b7 = Ui.ghostButton("OTHER AMOUNT");
        b7.addActionListener(e -> {
            setVisible(false);
            dispose();
            new Withdrawl(pin).setVisible(true);
        });

        Ui.Form form = new Ui.Form("SELECT A FAST CASH AMOUNT")
            .subtitle("One tap, no typing. Limits and machine cash still apply.")
            .row("", grid)
            .note("Available: " + Money.usd(Bank.get().balance(session))
                + "   |   Withdrawn today: " + Money.usd(Bank.get().spentToday(session)))
            .buttons(b7, b5, b6);

        Ui.shell(this, "ATM - Fast cash", form.panel(), 900, 860);
        setVisible(true);
    }

    private JButton preset(String text) {
        JButton b = Ui.button(text);
        b.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 20));
        b.setBackground(java.awt.Color.WHITE);
        b.setForeground(Ui.INK);
        b.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(Ui.INK),
            javax.swing.BorderFactory.createEmptyBorder(18, 10, 18, 10)));
        return b;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        BigDecimal amount = null;
        if (ae.getSource() == b1) {
            amount = PRESETS[0];
        } else if (ae.getSource() == b2) {
            amount = PRESETS[1];
        } else if (ae.getSource() == b3) {
            amount = PRESETS[2];
        } else if (ae.getSource() == b4) {
            amount = PRESETS[3];
        }
        if (amount == null) {
            return;
        }
        final BigDecimal requested = amount;
        Ui.run(this, () -> {
            Bank.CustomerSession session = AppSession.require(pin);
            MovementResult result = Bank.get().fastCash(session, requested,
                Ids.idempotencyKey());
            Ui.receipt(this, "Please take your cash", result.txn().reference(),
                result.txn().amount(), result.balance(),
                result.duplicate() ? "This withdrawal had already been dispensed."
                    : "Fast cash dispensed from the machine float.");
            setVisible(false);
            dispose();
            new Transactions(pin).setVisible(true);
        });
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new FastCash(AppSession.pin());
    }
}
