package atm.system.admin;

import atm.core.Bank;
import atm.core.model.AtmCash;
import atm.system.Ui;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * Machine cash: what the ATM holds by denomination, and the two ways staff change it.
 *
 * <p>Setting a level is for a recount; restocking is for the collection that has just come
 * back from the branch, which adds notes to what is already there. Both record who did it.
 */
public class AdminCash extends AdminShell {

    private JTable table;
    private JTextField notesField = Ui.textField(8);
    private JPanel editor;
    private List<AtmCash> current = new ArrayList<>();

    public AdminCash() {
        super("Back office - machine cash");
        build(content());
        refresh();
        setVisible(true);
    }

    @Override
    protected void build(JPanel area) {
        table = Ui.table(new String[] {"Denomination", "Notes held", "Value", "Updated"},
            new ArrayList<>(), 560);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelected();
            }
        });

        editor = new JPanel(new GridLayout(0, 1, 4, 6));
        editor.setOpaque(false);

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);
        body.add(Ui.scroll(table), BorderLayout.CENTER);
        body.add(editor, BorderLayout.EAST);
        area.add(body, BorderLayout.CENTER);

        JButton reload = Ui.ghostButton("REFRESH");
        reload.addActionListener(e -> Ui.run(this, this::refresh));

        JPanel filters = new Ui.Form("MACHINE CASH")
            .note("The machine can only dispense amounts it can make from these notes, so a "
                + "denomination it does not hold is never offered to a customer.")
            .buttons(reload)
            .panel();
        filters.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        area.add(filters, BorderLayout.SOUTH);
    }

    private void refresh() {
        current = Bank.get().admin().atmCashLevels();
        List<String[]> rows = new ArrayList<>();
        for (AtmCash c : current) {
            rows.add(new String[] {String.valueOf(c.denom()), String.valueOf(c.notes()),
                money(c.value()), stamp(c.updatedAt())});
        }
        AdminCustomers.fill(table, new String[] {"Denomination", "Notes held", "Value",
            "Updated"}, rows);
        status("Total cash in the machine: " + money(Bank.get().admin().atmCashTotal()));
        showSelected();
    }

    private void showSelected() {
        editor.removeAll();
        int row = table.getSelectedRow();
        if (row < 0 || row >= current.size()) {
            editor.add(Ui.hint("Select a denomination to change it."));
            editor.revalidate();
            editor.repaint();
            return;
        }
        AtmCash cash = current.get(row);
        String actor = AdminSession.require().username();

        editor.add(Ui.heading(cash.denom() + " notes"));
        editor.add(Ui.hint("Currently " + cash.notes() + " notes, " + money(cash.value())));
        editor.add(Ui.hint("Number of notes to add or set:"));
        editor.add(notesField);

        JButton restock = Ui.button("RESTOCK (add)");
        restock.addActionListener(e -> Ui.run(this, () -> {
            Bank.get().admin().restockCash(cash.denom(), Integer.parseInt(text(notesField)),
                actor);
            Ui.info(this, "Restocked " + cash.denom() + " notes.");
            refresh();
        }));

        JButton set = Ui.ghostButton("SET LEVEL (recount)");
        set.addActionListener(e -> Ui.run(this, () -> {
            if (!Ui.confirm(this, "Set " + cash.denom() + " notes to exactly "
                + text(notesField) + "?")) {
                return;
            }
            Bank.get().admin().setCashLevel(cash.denom(), Integer.parseInt(text(notesField)),
                actor);
            Ui.info(this, "Cash level set.");
            refresh();
        }));

        editor.add(restock);
        editor.add(set);
        editor.revalidate();
        editor.repaint();
    }

    public static void main(String[] args) {
        AdminSession.open(Bank.get().auth().signInAdmin("admin", "1234"));
        new AdminCash();
    }
}
