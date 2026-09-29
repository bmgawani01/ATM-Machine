package atm.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PIN and OTP hashing with PBKDF2-HMAC-SHA256 from the JDK (no extra library).
 *
 * <p>Stored format: {@code pbkdf2$<iterations>$<base64 salt>$<base64 hash>}. Verification is
 * constant time, so a timing attack cannot leak the PIN one character at a time.
 * The old plain-text {@code login.pin} column is still readable, so existing accounts keep
 * working and get upgraded to a hash on their first successful sign-in.
 */
public final class Passwords {

    private static final String ALGO = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    public static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hash(String secret, String saltB64) {
        try {
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(),
                Base64.getDecoder().decode(saltB64), ITERATIONS, KEY_BITS);
            byte[] key = SecretKeyFactory.getInstance(ALGO).generateSecret(spec).getEncoded();
            spec.clearPassword();
            return "pbkdf2$" + ITERATIONS + "$" + saltB64 + "$"
                + Base64.getEncoder().encodeToString(key);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    public static boolean verify(String secret, String stored) {
        if (secret == null || stored == null || stored.isEmpty()) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt,
                Integer.parseInt(parts[1]), KEY_BITS);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = SecretKeyFactory.getInstance(ALGO).generateSecret(spec).getEncoded();
            spec.clearPassword();
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            Log.errorQuiet("PIN verification failed", e);
            return false;
        }
    }

    /** True when the stored value is a legacy plain-text PIN that should be upgraded. */
    public static boolean isPlainText(String stored) {
        return stored != null && !stored.isEmpty() && !stored.startsWith("pbkdf2$");
    }

    public static int numericCode() {
        return 100_000 + RANDOM.nextInt(900_000);
    }
}
