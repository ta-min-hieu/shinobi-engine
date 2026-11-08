package dev.kage.client.core.world;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tô sáng khi bị nhắc tên (ADR-245) — tin có tên nhân vật mình (không phân biệt hoa thường, không dấu, đứng riêng
 * thành từ) từ NGƯỜI KHÁC. Thuần logic để test; {@code ChatCoordinator} dùng để tô màu dòng và báo một dòng.
 */
final class MentionWatch {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private String foldedName = "";

    /** Tên nhân vật đang chơi (lúc vào world). */
    void useOwnName(String name) {
        this.foldedName = fold(name);
    }

    /** Chữ thường, bỏ dấu, đ ⇒ d. Tĩnh để test. */
    static String fold(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('đ', 'd');
        return MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
    }

    /** {@code senderName} là chính mình (chứa tên mình — có thể kèm danh hiệu); chưa biết tên ⇒ {@code false}. */
    boolean isSelf(String senderName) {
        return !foldedName.isEmpty() && fold(senderName).contains(foldedName);
    }

    /** Tin của {@code senderName} có nhắc tên mình không (tin hệ thống / của chính mình ⇒ không). */
    boolean mentions(String senderName, String body) {
        // Tên người gửi có thể mang danh hiệu đứng trước (ADR-062) — chứa tên mình là tin của chính mình.
        if (foldedName.isEmpty() || senderName.isEmpty() || isSelf(senderName)) {
            return false;
        }
        String folded = fold(body);
        int from = 0;
        while (true) {
            int at = folded.indexOf(foldedName, from);
            if (at < 0) {
                return false;
            }
            int end = at + foldedName.length();
            if (isBoundary(folded, at - 1) && isBoundary(folded, end)) {
                return true;
            }
            from = at + 1;
        }
    }

    private static boolean isBoundary(String text, int index) {
        return index < 0 || index >= text.length() || !Character.isLetterOrDigit(text.charAt(index));
    }
}
