package atm.system;

import atm.core.Bank;
import atm.core.Ids;
import atm.core.Money;
import atm.core.model.Account;
import atm.core.model.Card;
import atm.core.model.Limits;
import atm.core.service.OtpService;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 * Card details and the one thing a card holder can do about the card: block it.
 *
 * <p>Blocking is deliberately not a free action. It needs a verification code, and it signs
 * the customer out of every session immediately, because a blocked card must not leave a
 * live session behind.
 */
public class CardServices extends JFrame implements ActionListener {

    private final Bank.CustomerSession session;
    private JButton blockButton;
    private JLabel statusLabel;

    CardServices(String pin) {
        this.session = AppSession.require(pin);

        blockButton = Ui.dangerButton("BLOCK THIS CARD");
        blockButton.addActionListener(this);

        statusLabel = Ui.hint("");

        Card card = Bank.get().cards().card(session.cardNo());
        Account account = Bank.get().cards().account(session.cardNo());
        Limits limits = Bank.get().limits(session);

        Ui.Form form = new Ui.Form("CARD SERVICES")
            .subtitle("Card " + Ids.maskCard(session.cardNo()))
            .row("Card number", Ui.readOnly(session.cardNo()))
            .row("Status", Ui.readOnly(card.status().label()))
            .row("Card expires", Ui.readOnly(card.expiresOn() == null ? "-"
                : card.expiresOn().toString()))
            .row("Issued", Ui.readOnly(card.issuedAt() == null ? "-"
                : card.issuedAt().toLocalDate().toString()))
            .row("Account", Ui.readOnly(account.accountNo()))
            .row("Account status", Ui.readOnly(account.status()))
            .section("Limits that apply")
            .note(Bank.get().limitsText(session).replace(" | ", "\n"))
            .note("Verification code needed above " + Money.usd(limits.requireOtpAbove()) + ".")
            .buttons(blockButton, Ui.backButton(this, pin));

        Ui.shell(this, "ATM - Card services", form.panel(), 900, 940);
        refresh();
        setVisible(true);
    }

    private void refresh() {
        Card card = Bank.get().cards().card(session.cardNo());
        boolean active = card.status() == atm.core.model.CardStatus.ACTIVE;
        blockButton.setEnabled(active);
        blockButton.setText(active ? "BLOCK THIS CARD" : "CARD IS " + card.status().label());
        statusLabel.setText(active
            ? "Blocking signs you out everywhere and staff must unblock the card."
            : "Contact the branch to have this card unblocked.");
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() != blockButton) {
            return;
        }
        Ui.run(this, () -> {
            if (!Ui.confirm(this, "Block card ending "
                + Ids.maskCard(session.cardNo()) + "?\n"
                + "You will be signed out and will need the branch to unblock it.")) {
                Ui.info(this, "Nothing was changed.");
                return;
            }
            String code = OtpPrompt.ask(this, session, OtpService.PURPOSE_CARD_BLOCK);
            if (code == null) {
                Ui.info(this, "Cancelled. The card is still active.");
                return;
            }
            Bank.get().blockCard(session, code);
            setVisible(false);
            dispose();
            AppSession.clear();
            Ui.info(null, "Your card has been blocked and every session was signed out.");
            new Login();
        });
    }

    public static void main(String[] args) {
        AppSession.ensureDemo();
        new CardServices(AppSession.pin());
    }
}
