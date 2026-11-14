package dev.kage.client.core.world;

import java.util.Locale;

/**
 * Rút gọn số sát thương (ADR-387): dưới 1.000 giữ nguyên; từ 1.000 ⇒ {@code 1.2k}; từ 1.000.000 ⇒ {@code 3.4M}. Một
 * chữ số thập phân, bỏ {@code .0}. Dấu thập phân luôn là dấu chấm (giống font chữ số HUD, không theo locale máy).
 */
final class CompactNumber {

    private static final long THOUSAND = 1_000L;
    private static final long MILLION = 1_000_000L;

    private CompactNumber() {}

    /** Chuỗi rút gọn của {@code value} (âm thì giữ dấu trừ). */
    static String format(long value) {
        long magnitude = Math.abs(value);
        if (magnitude < THOUSAND) {
            return Long.toString(value);
        }
        String sign = value < 0 ? "-" : "";
        return magnitude < MILLION ? sign + scaled(magnitude, THOUSAND) + "k" : sign + scaled(magnitude, MILLION) + "M";
    }

    private static String scaled(long magnitude, long unit) {
        long tenths = magnitude * 10L / unit; // cắt, không làm tròn — "1.29k" hiện "1.2k" chứ không phóng lên
        return tenths % 10L == 0L
                ? Long.toString(tenths / 10L)
                : String.format(Locale.ROOT, "%d.%d", tenths / 10L, tenths % 10L);
    }
}
