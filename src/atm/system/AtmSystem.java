package atm.system;

import atm.core.Bank;
import atm.core.Config;
import atm.core.Log;
import java.awt.EventQueue;
import javax.swing.JOptionPane;

/**
 * Application entry point.
 *
 * <p>It checks the database is reachable before showing anything, and installs a shutdown
 * hook that signs the customer out and closes the connection pool. The idle-session timeout
 * itself lives in {@link AppSession}, which is the only place that knows whether anyone is
 * signed in.
 *
 * @author Bright M. Gawani
 */
public class AtmSystem {

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (AppSession.isSignedIn()) {
                AppSession.signOut();
            }
            Bank.get().shutdown();
            Log.info("ATM stopped");
        }, "atm-shutdown"));

        EventQueue.invokeLater(() -> {
            if (Bank.get().admin().healthy()) {
                new Login().setVisible(true);
                return;
            }
            showStartupProblem();
            System.exit(1);
        });
    }

    private static void showStartupProblem() {
        Log.error("Cannot reach the database: " + Config.jdbcUrl());
        JOptionPane.showMessageDialog(null,
            "The ATM cannot reach the bank database.\n\n"
                + "Checked: " + Config.jdbcUrl() + "\n\n"
                + "Check that the database is running and that atm-db.properties is set, "
                + "then start the ATM again.",
            "Cannot start", JOptionPane.ERROR_MESSAGE);
    }
}
