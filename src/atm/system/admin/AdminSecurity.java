package atm.system.admin;

import atm.core.Bank;
import atm.core.Ids;
import atm.core.model.AuditEntry;
import atm.core.model.FailedLogin;
import atm.system.Ui;
import java.awt.BorderLayout;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;

/**
 * Security monitoring: who has been failing to sign in, and what staff have been doing.
 *
 * <p>This is the screen that makes the audit trail useful rather than decorative. Every
 * balance change, limit change, block and unblock made in the back office lands here with
 * the staff member's name against it.
 */
public class AdminSecurity extends AdminShell {

    private final JComboBox<String> actionBox = Ui.combo(new String[] {
        "All actions", "SIGN_IN", "SIGN_OUT", "FAILED_LOGIN", "OTP_ISSUED", "OTP_FAILED",
        "PIN_CHANGED", "CARD_BLOCKED", "CARD_UNBLOCKED", "DEPOSIT", "WITHDRAWAL", "FAST_CASH",
        "TRANSFER", "BILL_PAYMENT", "AIRTIME", "ADMIN_ACTION", "CASH_RESTOCK"
    });

    private JTable auditTable;

    public AdminSecurity() {
        super("Back office - security");
        build(content());
        setVisible(true);
    }

    @Override
    protected void build(JPanel area) {
        actionBox.addActionListener(e -> Ui.run(this, this::refresh));

        JButton reload = Ui.ghostButton("REFRESH");
        reload.addActionListener(e -> Ui.run(this, this::refresh));

        JPanel filters = new Ui.Form("AUDIT TRAIL")
            .subtitle("Both tables come straight from the audit log, newest first.")
            .row("Action", actionBox)
            .buttons(reload)
            .panel();
        filters.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        List<String[]> failed = new ArrayList<>();
        for (FailedLogin f : Bank.get().admin().recentFailedLogins(60)) {
            failed.add(new String[] {
                stamp(f.createdAt()),
                Ids.maskCard(f.cardNo()),
                f.reason(),
                Ui.orDash(f.ip())
            });
        }
        JTable failedTable = Ui.table(new String[] {"When", "Card or staff", "Reason", "From"},
            failed, 520);

        auditTable = Ui.table(new String[] {"When", "Action", "Actor / card", "Details",
            "From"}, new ArrayList<>(), 700);

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);
        body.add(titled("Failed sign-ins", failedTable), BorderLayout.WEST);
        body.add(titled("Audit trail", auditTable), BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(filters, BorderLayout.NORTH);
        top.add(body, BorderLayout.CENTER);
        area.add(top, BorderLayout.CENTER);

        refresh();
    }

    private JPanel titled(String caption, JTable table) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(Ui.heading(caption), BorderLayout.NORTH);
        p.add(Ui.scroll(table), BorderLayout.CENTER);
        return p;
    }

    private void refresh() {
        String action = actionBox.getSelectedIndex() == 0 ? null
            : (String) actionBox.getSelectedItem();
        List<AuditEntry> entries = Bank.get().admin().recentAudit(action, null, 150);
        List<String[]> rows = new ArrayList<>();
        for (AuditEntry e : entries) {
            rows.add(new String[] {
                stamp(e.createdAt()),
                e.action(),
                Ui.orDash(e.actor()) + " / " + Ids.maskCard(e.cardNo()),
                Ui.orDash(e.details()),
                Ui.orDash(e.ip())
            });
        }
        if (auditTable == null) {
            return;
        }
        AdminCustomers.fill(auditTable, new String[] {"When", "Action", "Actor / card",
            "Details", "From"}, rows);
        long since = Bank.get().admin().failedLoginsSince(LocalDateTime.now().minusDays(1), null);
        status(rows.size() + " audit entries shown. " + since
            + " failed sign-in(s) in the last 24 hours.");
    }
}
