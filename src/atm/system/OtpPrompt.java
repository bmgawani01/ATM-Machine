package atm.system;

import atm.core.Bank;
import atm.core.service.OtpService;
import java.awt.Component;
import javax.swing.JOptionPane;

/**
 * Asks the customer for their one-time code.
 *
 * <p>There is no SMS gateway wired up in this build, so the code is shown in the dialog and
 * written to the log rather than sent to the phone on the customer's profile. Swapping that
 * for a real gateway only changes {@link #deliver}.
 */
public final class OtpPrompt {

    private OtpPrompt() {
    }

    /** Asks for a code and returns what the customer typed, or null if they cancelled. */
    public static String ask(Component owner, Bank.CustomerSession session, String purpose) {
        String code = Bank.get().requestOtp(session, purpose);

        String message = "<html><body style='width:320px'>"
            + "<p>" + OtpService.describe(purpose) + "</p>"
            + "<p style='font-size:18px'><b>" + code + "</b></p>"
            + "<p style='color:#5A5A5A;font-size:11px'>No SMS gateway is connected in this "
            + "build, so the code is shown here instead of being texted. It expires shortly "
            + "and can only be used once.</p>"
            + "</body></html>";

        String entered = JOptionPane.showInputDialog(owner, message, "Verification required",
            JOptionPane.QUESTION_MESSAGE);
        return entered == null || entered.isBlank() ? null : entered.trim();
    }

    /**
     * Asks only when the amount really crosses the account's OTP threshold.
     *
     * @return the code to send with the transfer, or null when no code was needed
     */
    public static String askForTransfer(Component owner, Bank.CustomerSession session,
            String amount) {
        if (!Bank.get().transferNeedsOtp(session, amount)) {
            return null;
        }
        return ask(owner, session, OtpService.PURPOSE_TRANSFER);
    }
}
