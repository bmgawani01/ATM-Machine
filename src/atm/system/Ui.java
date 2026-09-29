package atm.system;

import atm.core.AtmException;
import atm.core.Log;
import atm.core.Money;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Shared look and feel for every screen, so the app reads as one program.
 *
 * <p>It also owns the two behaviours every screen must not get wrong: turning a bank error
 * into a message a customer can act on, and bouncing back to sign-in the moment a session
 * times out. Screens call {@link #run} instead of wrapping their handlers in try/catch.
 */
public final class Ui {

    public static final Color INK = new Color(0x1B, 0x1B, 0x1B);
    public static final Color MUTED = new Color(0x5A, 0x5A, 0x5A);
    public static final Color ACCENT = new Color(0x0B, 0x6B, 0x53);
    public static final Color DANGER = new Color(0xB3, 0x26, 0x1E);
    public static final Color PANEL = new Color(0xF4, 0xF5, 0xF7);
    public static final Color LINE = new Color(0xD8, 0xDC, 0xE0);

    private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private static final Font BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font HEAD = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    private static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private Ui() {
    }

    // ------------------------------------------------------------------ widgets

    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(HEAD);
        l.setForeground(INK);
        return l;
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        l.setForeground(INK);
        return l;
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(BOLD);
        l.setForeground(INK);
        return l;
    }

    public static JLabel hint(String text) {
        JLabel l = new JLabel(text);
        l.setFont(SMALL);
        l.setForeground(MUTED);
        return l;
    }

    /** The white-on-dark button the original screens used, kept consistent. */
    public static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFont(BOLD);
        b.setBackground(INK);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** A softer button for secondary actions such as BACK. */
    public static JButton ghostButton(String text) {
        JButton b = button(text);
        b.setBackground(PANEL);
        b.setForeground(INK);
        b.setBorder(BorderFactory.createLineBorder(LINE));
        return b;
    }

    public static JButton dangerButton(String text) {
        JButton b = button(text);
        b.setBackground(DANGER);
        return b;
    }

    public static JTextField textField(int columns) {
        JTextField f = new JTextField(columns);
        f.setFont(BODY);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        return f;
    }

    public static JPasswordField pinField(int columns) {
        JPasswordField f = new JPasswordField(columns);
        f.setFont(BODY);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        return f;
    }

    public static JComboBox<String> combo(String[] items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setFont(BODY);
        c.setBackground(Color.WHITE);
        return c;
    }

    public static JTextArea textArea(int rows, int columns) {
        JTextArea a = new JTextArea(rows, columns);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        a.setBackground(Color.WHITE);
        return a;
    }

    /** White card with a soft border, used to group the controls on a screen. */
    public static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE),
            BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        return p;
    }

    public static JFrame frame(String title, int width, int height) {
        JFrame f = new JFrame(title);
        f.setSize(width, height);
        f.setLocationRelativeTo(null);
        f.getContentPane().setBackground(Color.WHITE);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        return f;
    }

    /** The ATM photo as a background label, the way the original screens layered it. */
    public static JLabel background(String resource, int width, int height) {
        ImageIcon icon = new ImageIcon(Ui.class.getResource(resource));
        Image scaled = icon.getImage().getScaledInstance(width, height, Image.SCALE_DEFAULT);
        JLabel l = new JLabel(new ImageIcon(scaled));
        l.setBounds(0, 0, width, height);
        return l;
    }

    public static ImageIcon icon(String resource, int size) {
        ImageIcon icon = new ImageIcon(Ui.class.getResource(resource));
        Image scaled = icon.getImage().getScaledInstance(size, size, Image.SCALE_DEFAULT);
        return new ImageIcon(scaled);
    }

    public static void at(JComponent c, int x, int y, int w, int h) {
        c.setBounds(x, y, w, h);
    }

    // ----------------------------------------------------------------- messages

    public static void info(JFrame owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "ATM", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(JFrame owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "ATM", JOptionPane.ERROR_MESSAGE);
    }

    public static boolean confirm(JFrame owner, String question) {
        return JOptionPane.showConfirmDialog(owner, question, "Please confirm",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Closes every open screen, used when a session is lost from under the app. */
    public static void closeAll() {
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (w instanceof JFrame && w.isVisible()) {
                w.setVisible(false);
                w.dispose();
            }
        }
    }

    /**
     * Runs a button action, turning any bank error into a message the customer can act on
     * and sending them back to sign-in if the session has expired.
     */
    public static void run(JFrame owner, Runnable action) {
        try {
            action.run();
        } catch (AtmException e) {
            if (e.reason() == AtmException.Reason.SESSION_EXPIRED) {
                if (owner != null) {
                    owner.setVisible(false);
                    owner.dispose();
                }
                AppSession.clear();
                error(null, e.userMessage());
                new Login().setVisible(true);
                return;
            }
            error(owner, e.userMessage());
        } catch (RuntimeException e) {
            Log.error("Unexpected error on " + (owner == null ? "a screen" : owner.getTitle()), e);
            error(owner, "Something went wrong. Please try again.");
        }
    }

    // ------------------------------------------------------------------ receipts

    /** The confirmation a customer sees after money moves. */
    public static void receipt(JFrame owner, String title, String reference, BigDecimal amount,
            BigDecimal balance, String extra) {
        StringBuilder sb = new StringBuilder("<html><div style='font-family:sans-serif'>");
        sb.append("<b style='font-size:16px'>").append(title).append("</b><br><br>");
        sb.append("Amount: ").append(Money.usd(amount)).append("<br>");
        if (reference != null && !reference.isBlank()) {
            sb.append("Reference: ").append(reference).append("<br>");
        }
        if (balance != null) {
            sb.append("Balance: ").append(Money.usd(balance)).append("<br>");
        }
        if (extra != null && !extra.isBlank()) {
            sb.append(extra).append("<br>");
        }
        sb.append("</div></html>");
        JOptionPane.showMessageDialog(owner, sb.toString(), "Transaction complete",
            JOptionPane.INFORMATION_MESSAGE);
    }

    // -------------------------------------------------------------------- tables

    /** A read-only table for the list screens. */
    public static JTable table(String[] headers, List<String[]> rows, int width) {
        DefaultTableModel model = new DefaultTableModel(headers, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (String[] row : rows) {
            model.addRow(row);
        }
        JTable t = new JTable(model);
        t.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        t.setRowHeight(24);
        t.setFillsViewportHeight(true);
        t.setAutoCreateRowSorter(true);
        t.getTableHeader().setFont(BOLD);
        t.getTableHeader().setReorderingAllowed(false);
        Dimension d = new Dimension(width, 20);
        t.setPreferredScrollableViewportSize(d);
        return t;
    }

    public static JScrollPane scroll(JComponent c) {        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createLineBorder(LINE));
        s.getViewport().setBackground(Color.WHITE);
        return s;
    }

    public static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    // -------------------------------------------------------------- screen shell

    /** Puts a form card on the ATM photo and sizes the window around it. */
    public static JFrame shell(JFrame frame, String title, JComponent card, int width,
            int height) {
        frame.setTitle(title);
        frame.setLayout(null);
        frame.add(background("/icons/atm.jpg", width, height));
        int cardWidth = Math.max(card.getPreferredSize().width + 40, 420);
        int cardHeight = card.getPreferredSize().height;
        card.setBounds((width - cardWidth) / 2, 70, cardWidth, cardHeight);
        frame.add(card);
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null);
        return frame;
    }

    /** A BACK button that closes this screen and reopens the transaction menu. */
    public static JButton backButton(JFrame from, String pin) {
        JButton b = ghostButton("BACK");
        b.addActionListener(e -> {
            from.setVisible(false);
            from.dispose();
            new Transactions(pin).setVisible(true);
        });
        return b;
    }

    /**
     * A button that closes this screen and goes back to sign-in.
     *
     * <p>CANCEL takes the customer's card back, so the session ends here. Without the sign-out
     * the session would stay alive behind the login screen, letting a second card be inserted
     * without the first one ever being released.
     */
    public static JButton cancelButton(JFrame from) {
        return cancelButton(from, null);
    }

    /** As {@link #cancelButton(JFrame)}, but runs {@code cleanup} first, e.g. to drop state. */
    public static JButton cancelButton(JFrame from, Runnable cleanup) {
        JButton b = ghostButton("CANCEL");
        b.addActionListener(e -> {
            from.setVisible(false);
            from.dispose();
            AppSession.signOut();
            if (cleanup != null) {
                cleanup.run();
            }
            new Login();
        });
        return b;
    }

    /**
     * Runs {@code release} when the page closes, unless {@code stillNeeded} says the state is
     * being handed to another page.
     *
     * <p>The three signup pages stage an application in memory and pass it to the next page, so
     * closing normally must not throw it away. Every other way out means the customer walked
     * away, and the staged application would otherwise sit in the map for the life of the JVM.
     * Listening on {@code windowClosed} rather than {@code windowClosing} covers the customer
     * clicking the X button and the window being disposed for any other reason.
     */
    public static void onAbandonedClose(JFrame frame, java.util.function.BooleanSupplier stillNeeded,
            Runnable release) {
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (!stillNeeded.getAsBoolean()) {
                    release.run();
                }
            }
        });
    }

    /** A read-only field that shows a value, styled like the input fields. */
    public static JTextField readOnly(String value) {
        JTextField f = textField(20);
        f.setText(value);
        f.setEditable(false);
        f.setBackground(PANEL);
        return f;
    }

    // --------------------------------------------------------------------- form

    /**
     * A stacked label/field form on a white card. Screens build one of these instead of
     * hand-placing every label, so spacing and typography stay identical across the app.
     */
    public static final class Form {

        private final JPanel card = card();
        private int row;
        private JLabel noteLabel;

        public Form(String heading) {
            card.setLayout(new java.awt.GridBagLayout());
            add(Ui.title(heading), 2, 0, new Insets(0, 4, 0, 4));
            row = 1;
        }

        public Form subtitle(String text) {
            add(Ui.hint(text), 2, row++, new Insets(0, 0, 8, 0));
            return this;
        }

        public Form row(String labelText, JComponent field) {
            java.awt.GridBagConstraints g = base(0, row++);
            g.anchor = java.awt.GridBagConstraints.LINE_END;
            g.insets = new Insets(8, 4, 8, 10);
            card.add(new JLabel(labelText), g);
            g = base(1, row - 1);
            g.fill = java.awt.GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            g.insets = new Insets(8, 0, 8, 4);
            card.add(field, g);
            return this;
        }

        /** A full-width note, for hints and read-only summaries. */
        public Form note(String text) {
            noteLabel = Ui.hint(text);
            add(noteLabel, 2, row++, new Insets(4, 4, 4, 4));
            return this;
        }

        /**
         * Rewrites the text of the last {@link #note(String)} without adding another row.
         *
         * <p>Needed when the note depends on something the customer changes after the form is
         * built, such as which biller is selected.
         */
        public Form noteText(String text) {
            if (noteLabel == null) {
                return note(text);
            }
            noteLabel.setText(text);
            return this;
        }

        public Form section(String text) {
            add(Ui.heading(text), 2, row++, new Insets(14, 4, 4, 4));
            return this;
        }

        public Form buttons(JComponent... buttons) {
            JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 6));
            bar.setOpaque(false);
            for (JComponent b : buttons) {
                bar.add(b);
            }
            add(bar, 2, row, new Insets(16, 4, 0, 4));
            row++;
            return this;
        }

        public JPanel panel() {
            return card;
        }

        private void add(JComponent c, int gridx, int gridy, Insets insets) {
            java.awt.GridBagConstraints g = base(gridx, gridy);
            g.insets = insets;
            card.add(c, g);
        }

        private java.awt.GridBagConstraints base(int gridx, int gridy) {
            java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
            g.gridx = gridx;
            g.gridy = gridy;
            g.insets = new Insets(0, 4, 0, 4);
            return g;
        }
    }
}
