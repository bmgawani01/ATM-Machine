package atm.core.service;

import atm.core.Database;
import atm.core.Log;
import atm.core.Money;
import atm.core.dao.TxnDao;
import atm.core.model.Txn;
import atm.core.model.TransactionType;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Mini statement, transaction history and CSV export. */
public class StatementService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final Database db = Database.get();
    private final TxnDao txns = new TxnDao();

    public List<Txn> history(String accountNo, LocalDate from, LocalDate to, TransactionType type,
            int limit) {
        return db.read(c -> txns.listByAccount(c, accountNo, from, to, type,
            Math.max(1, Math.min(limit, 1000))));
    }

    /** The last {@code lines} movements, newest first, as printable lines. */
    public List<String> miniStatement(String accountNo, int lines) {
        List<String> out = new ArrayList<>();
        for (Txn t : history(accountNo, null, null, null, lines)) {
            out.add(line(t));
        }
        return out;
    }

    /** One printable row: date, type, signed amount, running balance, reference. */
    public String line(Txn t) {
        return String.format("%-17s %-16s %14s  %14s  %s",
            t.createdAt() == null ? "" : t.createdAt().format(STAMP),
            t.type().label(),
            t.signedAmount(),
            Money.usd(t.balanceAfter()),
            t.reference());
    }

    /** Rows for a JTable: date, type, amount, balance, reference, note. */
    public List<String[]> rows(String accountNo, LocalDate from, LocalDate to,
            TransactionType type, int limit) {
        List<String[]> out = new ArrayList<>();
        for (Txn t : history(accountNo, from, to, type, limit)) {
            out.add(new String[] {
                t.createdAt() == null ? "" : t.createdAt().format(STAMP),
                t.type().label(),
                t.signedAmount(),
                Money.plain(t.balanceAfter()),
                t.reference(),
                t.note() == null ? "" : t.note()
            });
        }
        return out;
    }

    /** Total money in and money out over a period, for the profile screen. */
    public BigDecimal totalIn(String accountNo, LocalDate from, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Txn t : history(accountNo, from, to, null, 1000)) {
            if (t.isCredit()) {
                sum = sum.add(t.amount());
            }
        }
        return Money.scale(sum);
    }

    public BigDecimal totalOut(String accountNo, LocalDate from, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Txn t : history(accountNo, from, to, null, 1000)) {
            if (!t.isCredit()) {
                sum = sum.add(t.amount());
            }
        }
        return Money.scale(sum);
    }

    public String toCsv(String accountNo, LocalDate from, LocalDate to) {
        StringBuilder sb = new StringBuilder();
        sb.append("Account,Date,Type,Amount,Balance,Reference,Channel,Notes\n");
        for (Txn t : history(accountNo, from, to, null, 1000)) {
            sb.append(csv(accountNo)).append(',')
                .append(csv(t.createdAt() == null ? "" : t.createdAt().format(STAMP))).append(',')
                .append(csv(t.type().label())).append(',')
                .append(csv(t.signedAmount())).append(',')
                .append(csv(Money.plain(t.balanceAfter()))).append(',')
                .append(csv(t.reference())).append(',')
                .append(csv(t.channel())).append(',')
                .append(csv(t.note())).append('\n');
        }
        return sb.toString();
    }

    /** Writes a statement next to the user's home and returns the file. */
    public Path export(String accountNo, LocalDate from, LocalDate to) {
        try {
            Path dir = Path.of(System.getProperty("user.home", "."), "atm-exports");
            Files.createDirectories(dir);
            Path file = dir.resolve("statement-" + accountNo + "-" + LocalDate.now() + ".csv");
            Files.writeString(file, toCsv(accountNo, from, to), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new atm.core.AtmException(atm.core.AtmException.Reason.UNAVAILABLE,
                "Could not write the statement file: " + e.getMessage(), e);
        }
    }

    /** Turns a report table into a CSV file, for the admin's export button. */
    public Path exportReport(String fileName, String[] headers, List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headers.length; i++) {
            sb.append(i == 0 ? "" : ",").append(csv(headers[i]));
        }
        sb.append('\n');
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                sb.append(i == 0 ? "" : ",").append(csv(row[i]));
            }
            sb.append('\n');
        }
        try {
            Path dir = Path.of(System.getProperty("user.home", "."), "atm-exports");
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName + "-" + LocalDate.now() + ".csv");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            Log.errorQuiet("Report export failed", e);
            throw new atm.core.AtmException(atm.core.AtmException.Reason.UNAVAILABLE,
                "Could not write the report file: " + e.getMessage(), e);
        }
    }

    private static String csv(String value) {
        String v = value == null ? "" : value;
        return '"' + v.replace("\"", "\"\"") + '"';
    }
}
