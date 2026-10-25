package dev.kage.gm.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Một mã quà (ADR-104). Mã luôn CHỮ HOA, 4–24 ký tự {@code [A-Z0-9_-]}.
 *
 * @param itemTemplateId {@code <= 0} = không có vật phẩm
 */
public record GiftCode(
        String code, long gold, long itemTemplateId, int quantity, int maxUses, int uses, Instant expiresAt) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9_-]{4,24}");

    /** Chuẩn hoá chữ người gõ: bỏ khoảng trắng hai đầu, viết hoa. */
    public static String normalize(String raw) {
        return raw.strip().toUpperCase(Locale.ROOT);
    }

    /** Đúng định dạng mã (sau {@link #normalize}). */
    public static boolean isWellFormed(String normalized) {
        return FORMAT.matcher(normalized).matches();
    }

    /** Có vật phẩm đính kèm. */
    public boolean hasItem() {
        return itemTemplateId > 0 && quantity > 0;
    }
}
