package atm.system.admin;

import atm.core.Bank;
import atm.core.Ids;
import atm.core.model.Card;
import atm.core.model.CardStatus;
import atm.system.Ui;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * Card management: unblock a card someone is locked out of, extend an expiring card, reset a
 * lock after checking identity, or block a lost card from the branch.
 */
public class AdminCards extends AdminShell {

    private final JTextField searchField = Ui.textField(20);
    private final JComboBox<String> statusBox = Ui.combo(new String[] {"Any status",
        "ACTIVE", "BLOCKED", "EXPIRED"});
    private JTable table;
    private List<Card> current = new ArrayList<>();
    private JPanel actions;

    public AdminCards() {
        super("Back office - cards");
        build(content());
        refresh();
        setVisible(true);
    }

    @Override
    protected void build(JPanel area) {
        JButton find = Ui.button("SEARCH");
        find.addActionListener(e -> Ui.run(this, this::refresh));

        JButton reload = Ui.ghostButton("REFRESH");
        reload.addActionListener(e -> Ui.run(this, this::refresh));

        JPanel filters = new Ui.Form("CARD MANAGEMENT")
            .row("Card number or account number", searchField)
            .row("Status", statusBox)
            .buttons(find, reload)
            .panel();
        filters.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        table = Ui.table(new String[] {"Card", "Account", "Status", "Failed", "Locked until",
            "Expires", "Last used"}, new ArrayList<>(), 760);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelected();
            }
        });

        actions = new JPanel(new GridLayout(0, 1, 4, 6));
        actions.setOpaque(false);

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);
        body.add(Ui.scroll(table), BorderLayout.CENTER);
        body.add(actions, BorderLayout.EAST);
        area.add(body, BorderLayout.CENTER);
        area.add(filters, BorderLayout.NORTH);
    }

    private void refresh() {
        List<Card> cards = Bank.get().admin().listCards(text(searchField), 200);
        CardStatus wanted = statusBox.getSelectedIndex() == 0 ? null
            : CardStatus.of((String) statusBox.getSelectedItem());
        current = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        for (Card c : cards) {
            if (wanted != null && c.status() != wanted) {
                continue;
            }
            current.add(c);
            rows.add(new String[] {Ids.maskCard(c.cardNo()), c.accountNo(),
                c.status().label(), String.valueOf(c.failedAttempts()),
                stamp(c.lockedUntil()),
                c.expiresOn() == null ? "-" : c.expiresOn().toString(),
                c.lastUsedAt() == null ? "never" : c.lastUsedAt().toLocalDate().toString()});
        }
        AdminCustomers.fill(table, new String[] {"Card", "Account", "Status", "Failed",
            "Locked until", "Expires", "Last used"}, rows);
        status(current.size() + " card(s) matched. "
            + Bank.get().admin().cardCount(CardStatus.ACTIVE) + " active, "
            + Bank.get().admin().cardCount(CardStatus.BLOCKED) + " blocked, "
            + Bank.get().admin().cardCount(CardStatus.EXPIRED) + " expired.");
        showSelected();
    }

    private void showSelected() {
        actions.removeAll();
        int row = table.getSelectedRow();
        if (row < 0 || row >= current.size()) {
            actions.add(Ui.hint("Select a card to manage it."));
            actions.revalidate();
            actions.repaint();
            return;
        }
        Card card = current.get(row);
        String actor = AdminSession.require().username();

        actions.add(Ui.heading(Ids.maskCard(card.cardNo())));
        actions.add(Ui.hint("Status: " + card.status().label()));

        JButton unblock = Ui.button("UNBLOCK");
        unblock.addActionListener(e -> Ui.run(this, () -> {
            Bank.get().admin().setCardStatus(card.cardNo(), CardStatus.ACTIVE, actor);
            Bank.get().admin().resetCardLock(card.cardNo(), actor);
            Ui.info(this, "Card unblocked.");
            refresh();
        }));
        JButton resetLock = Ui.button("RESET LOCK");
        resetLock.addActionListener(e -> Ui.run(this, () -> {
            Bank.get().admin().resetCardLock(card.cardNo(), actor);
            Ui.info(this, "Lock reset and failed attempts cleared.");
            refresh();
        }));
        JButton block = Ui.dangerButton("BLOCK");
        block.addActionListener(e -> Ui.run(this, () -> {
            if (!Ui.confirm(this, "Block card " + Ids.maskCard(card.cardNo())
                + "?\nEvery session on this card will be signed out.")) {
                return;
            }
            Bank.get().admin().setCardStatus(card.cardNo(), CardStatus.BLOCKED, actor);
            Ui.info(this, "Card blocked. Existing sessions were signed out.");
            refresh();
        }));
        JButton extend = Ui.button("EXTEND 12 MONTHS");
        extend.addActionListener(e -> Ui.run(this, () -> {
            Bank.get().admin().extendCard(card.cardNo(), 12, actor);
            Ui.info(this, "Card extended by 12 months.");
            refresh();
        }));
        JButton setPin = Ui.button("SET PIN");
        setPin.addActionListener(e -> {
            String pin = javax.swing.JOptionPane.showInputDialog(this,
                "New 4 to 6 digit PIN for this card:");
            if (pin == null || pin.isBlank()) {
                return;
            }
            Ui.run(this, () -> {
                Bank.get().admin().setCardPin(card.cardNo(), pin.trim(), actor);
                Ui.info(this, "PIN set. Tell the customer their new PIN privately.");
                refresh();
            });
        });

        for (JButton b : new JButton[] {unblock, resetLock, block, extend, setPin}) {
            actions.add(b);
        }
        actions.revalidate();
        actions.repaint();
    }

    public static void main(String[] args) {
        AdminSession.open(Bank.get().auth().signInAdmin("admin", "1234"));
        new AdminCards();
    }
}
