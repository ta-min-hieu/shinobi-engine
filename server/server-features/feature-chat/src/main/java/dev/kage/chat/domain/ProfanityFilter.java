package dev.kage.chat.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Lọc từ cấm PLACEHOLDER — danh sách nội bộ, so khớp chuỗi con không phân biệt hoa/thường, KHÔNG
 * homoglyph-aware (khác cảnh báo SECURITY.md §12 với tên nhân vật, cùng lý do: dự án cá nhân, rủi
 * ro thấp, để mức tối thiểu có chủ đích — GAMEPLAY.md §10 chỉ yêu cầu "lọc từ cấm" tồn tại, chưa
 * chốt danh sách thật). Thay bằng nguồn cấu hình/DB khi có danh sách chính thức. KHÔNG xử lý XSS
 * (cảnh báo RIÊNG ở GAMEPLAY.md §10) — client tự hiển thị chat dạng text thuần, không render HTML,
 * nên không cần escape ở đây.
 *
 * <p><b>So khớp NGUYÊN TỪ, không phải chuỗi con (sửa 2026-10-02).</b> Bản cũ dùng
 * {@code contains}, nên từ cấm "ngu" chặn mọi chữ bắt đầu bằng nó: cảnh giới "Nguyên Anh"
 * (ADR-038), họ "Nguyễn", "nguy hiểm", "nguyên liệu" — người chơi không gõ nổi tên cảnh giới của
 * chính mình. Giờ một lần khớp chỉ tính khi hai bên nó KHÔNG dính chữ/số/dấu tổ hợp. Văn bản được
 * chuẩn hoá NFC trước: bộ gõ dạng tổ hợp (NFD) gửi "ngủ" thành "ngu" + dấu hỏi rời — không chuẩn
 * hoá thì nó trông như từ cấm, còn "chó chết" gõ dạng NFD lại lọt qua.
 */
public final class ProfanityFilter {

    // PLACEHOLDER — vài từ mẫu để chứng minh cơ chế hoạt động, KHÔNG phải danh sách sản phẩm thật.
    private static final Set<String> BANNED_WORDS = Set.of("ngu", "chó chết");

    private ProfanityFilter() {}

    public static boolean containsBannedWord(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        for (String bannedWord : BANNED_WORDS) {
            if (containsAsWholeWord(normalized, Normalizer.normalize(bannedWord, Normalizer.Form.NFC))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAsWholeWord(String text, String word) {
        int from = text.indexOf(word);
        while (from >= 0) {
            int end = from + word.length();
            boolean startsAtBoundary = from == 0 || !isWordPart(text.codePointBefore(from));
            boolean endsAtBoundary = end == text.length() || !isWordPart(text.codePointAt(end));
            if (startsAtBoundary && endsAtBoundary) {
                return true;
            }
            from = text.indexOf(word, from + 1);
        }
        return false;
    }

    /** Chữ, số, hoặc dấu tổ hợp còn sót sau NFC (vẫn là một phần của chữ đứng trước nó). */
    private static boolean isWordPart(int codePoint) {
        return Character.isLetterOrDigit(codePoint)
                || Character.getType(codePoint) == Character.NON_SPACING_MARK
                || Character.getType(codePoint) == Character.COMBINING_SPACING_MARK;
    }
}
