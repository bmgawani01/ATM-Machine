package atm.system.admin;

import atm.core.AtmException;
import atm.core.Bank;
import atm.core.dao.AdminUserDao;

/**
 * Who is in the back office.
 *
 * <p>Staff sign-in does not mint a session the way a customer session does, so this holder
 * keeps the signed-in staff record and re-reads it from the database on every screen. A staff
 * account that has been disabled or locked in the meantime therefore stops working
 * immediately instead of at the end of a long shift.
 */
public final class AdminSession {

    private static volatile String username;

    private AdminSession() {
    }

    public static void open(AdminUserDao.Admin admin) {
        username = admin.username();
    }

    /** The signed-in staff member, or a SESSION_EXPIRED error if there is not one. */
    public static AdminUserDao.Admin require() {
        String user = username;
        if (user == null) {
            throw new AtmException(AtmException.Reason.SESSION_EXPIRED,
                "Sign in to the back office to continue");
        }
        AdminUserDao.Admin admin = Bank.get().admin().findAdmin(user);
        if (admin == null) {
            clear();
            throw new AtmException(AtmException.Reason.SESSION_EXPIRED,
                "Your staff account no longer exists. Sign in again");
        }
        if (!admin.isActive()) {
            clear();
            throw new AtmException(AtmException.Reason.BLOCKED,
                "Your staff account has been disabled");
        }
        if (admin.isLocked(java.time.LocalDateTime.now())) {
            clear();
            throw new AtmException(AtmException.Reason.LOCKED,
                "Your staff account is locked. Try again later.");
        }
        return admin;
    }

    public static boolean isSignedIn() {
        return username != null;
    }

    public static void clear() {
        username = null;
    }
}
