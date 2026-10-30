package dev.kage.account.domain;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Luật khoá an toàn (ADR-115): PIN 4–6 chữ số, băm PBKDF2-HMAC-SHA256 có muối (JDK, không thư viện mới), sai
 * {@value #MAX_FAILED_ATTEMPTS} lần liên tiếp thì khoá {@link #LOCK_DURATION}.
 */
public final class SecurityPinRules {

    /** Sai liên tiếp tới mức này thì khoá. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** Thời gian khoá sau khi sai quá số lần. */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private static final Pattern PIN_FORMAT = Pattern.compile("\\d{4,6}");
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private SecurityPinRules() {}

    /** PIN đúng dạng 4–6 chữ số ASCII. */
    public static boolean isValidFormat(String pin) {
        return pin != null && PIN_FORMAT.matcher(pin).matches();
    }

    /** Muối mới ngẫu nhiên (hex). */
    public static String newSaltHex() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return HEX.formatHex(salt);
    }

    /** Băm PIN với muối (hex vào, hex ra). */
    public static String hashHex(String pin, String saltHex) {
        char[] chars = pin.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, HEX.parseHex(saltHex), ITERATIONS, KEY_BITS);
        try {
            return HEX.formatHex(
                    SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException e) {
            // PBKDF2WithHmacSHA256 là thuật toán bắt buộc của mọi JDK — tới đây là JDK hỏng, không phải input xấu.
            throw new IllegalStateException("JDK thiếu " + ALGORITHM, e);
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(chars, '\0');
        }
    }

    /** So khớp thời gian hằng — không lộ vị trí byte sai qua thời gian phản hồi. */
    public static boolean matches(String pin, SecurityPinPort.StoredPin stored) {
        return MessageDigest.isEqual(HEX.parseHex(hashHex(pin, stored.saltHex())), HEX.parseHex(stored.hashHex()));
    }
}
