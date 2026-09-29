package atm.system.admin;

import atm.core.Bank;
import atm.core.Money;
import atm.system.Ui;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * Reports: the day's movements by day, by type and the busiest customers, with CSV export.
 * The figures come from {@code atm.core.ReportDao} through {@code atm.core.AdminService}, so
 * a report can never disagree with the dashboard.
 */
public class AdminReports extends AdminShell {

    private final JTextField fromField = Ui.textField(12);
    private final JTextField toField = Ui.textField(12);
    private JTable dailyTable;
    private JTable typeTable;
    private JTable topTable;

    public AdminReports() {
        super("Back office - reports");
        fromField.setText(LocalDate.now().withDayOfMonth(1).toString());
        toField.setText(LocalDate.now().toString());
        build(content());
        refresh();
        setVisible(true);
    }

    @Override
    protected void build(JPanel area) {
        JButton run = Ui.button("RUN REPORTS");
        run.addActionListener(e -> Ui.run(this, this::refresh));
        JButton export = Ui.ghostButton("EXPORT ALL CSVs");
        export.addActionListener(e -> Ui.run(this, this::export));

        JPanel filters = new Ui.Form("REPORTS")
            .row("From (yyyy-mm-dd)", fromField)
            .row("To (yyyy-mm-dd)", toField)
            .buttons(run, export)
            .panel();
        filters.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        dailyTable = Ui.table(new String[] {"Day", "Transactions", "Deposits", "Withdrawals",
            "Transfers", "Bills"}, new java.util.ArrayList<>(), 700);
        typeTable = Ui.table(new String[] {"Type", "Count", "Value"},
            new java.util.ArrayList<>(), 380);
        topTable = Ui.table(new String[] {"Customer", "Card", "Transactions", "Value"},
            new java.util.ArrayList<>(), 420);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(filters, BorderLayout.NORTH);

        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setOpaque(false);
        right.add(titled("By type", typeTable), BorderLayout.NORTH);
        right.add(titled("Top customers", topTable), BorderLayout.CENTER);
        top.add(right, BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);
        body.add(titled("Daily movements", dailyTable), BorderLayout.CENTER);
        top.add(body, BorderLayout.CENTER);
        area.add(top, BorderLayout.CENTER);
    }

    private JPanel titled(String caption, JTable table) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(Ui.heading(caption), BorderLayout.NORTH);
        p.add(Ui.scroll(table), BorderLayout.CENTER);
        return p;
    }

    private LocalDate from() {
        try {
            return LocalDate.parse(text(fromField));
        } catch (DateTimeParseException e) {
            return LocalDate.now().withDayOfMonth(1);
        }
    }

    private LocalDate to() {
        try {
            return LocalDate.parse(text(toField));
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }

    private void refresh() {
        LocalDate from = from();
        LocalDate to = to();

        // ReportDao returns the count last, so reorder it to lead the row.
        java.util.List<String[]> daily = new java.util.ArrayList<>();
        for (String[] r : Bank.get().admin().dailyReport(from, to)) {
            daily.add(new String[] {r[0], r[5], usd(r[1]), usd(r[2]), usd(r[3]), usd(r[4])});
        }
        AdminCustomers.fill(dailyTable, new String[] {"Day", "Transactions", "Deposits",
            "Withdrawals", "Transfers", "Bills"}, daily);

        java.util.List<String[]> types = new java.util.ArrayList<>();
        for (String[] r : Bank.get().admin().typeReport(from, to)) {
            types.add(new String[] {r[0], r[1], usd(r[2])});
        }
        AdminCustomers.fill(typeTable, new String[] {"Type", "Count", "Value"}, types);

        java.util.List<String[]> top = new java.util.ArrayList<>();
        for (String[] r : Bank.get().admin().topCustomers(from, to, 10)) {
            top.add(new String[] {r[0], atm.core.Ids.maskCard(r[1]), r[2], usd(r[3])});
        }
        AdminCustomers.fill(topTable, new String[] {"Customer", "Card", "Transactions",
            "Value"}, top);

        status("Reports from " + from + " to " + to + ".");
    }

    private static String usd(String plain) {
        try {
            return Money.usd(new java.math.BigDecimal(plain.replace(",", "").trim()));
        } catch (NumberFormatException e) {
            return plain;
        }
    }

    private void export() {
        LocalDate from = from();
        LocalDate to = to();
        StringBuilder where = new StringBuilder();
        where.append(Bank.get().admin().dailyCsv(from, to)).append('\n');
        where.append(Bank.get().admin().typeCsv(from, to)).append('\n');
        where.append(Bank.get().admin().topCustomersCsv(from, to, 25));
        Ui.info(this, "Reports written to:\n" + where);
    }

    public static void main(String[] args) {
        AdminSession.open(Bank.get().auth().signInAdmin("admin", "1234"));
        new AdminReports();
    }
}
