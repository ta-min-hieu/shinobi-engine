package dev.kage.chat.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Lọc từ cấm PLACEHOLDER — danh sách nội bộ, so khớp chuỗi con không phân biệt hoa/thường, KHÔNG
 * homoglyph-aware (khác cảnh báo SECURITY.md §12 với tên nhân vật, cùng lý do: dự án cá nhân, rủi
 * ro thấp, để mức tối thiểu có chủ đích — GAMEPLAY.md §10 chỉ yêu cầu "lọc từ cấm" tồn tại, chưa
 * chốt danh sách thật). Thay bằng nguồn cấu hình/DB khi có danh sách chính thức. KHÔNG xử lý XSS
 * (cảnh báo RIÊNG ở GAMEPLAY.md §10) — client tự hiển thị chat dạng text thuần, không render HTML,
 * nên không cần escape ở đây.
 */
public final class ProfanityFilter {

    // PLACEHOLDER — vài từ mẫu để chứng minh cơ chế hoạt động, KHÔNG phải danh sách sản phẩm thật.
    private static final Set<String> BANNED_WORDS = Set.of("ngu", "chó chết");

    private ProfanityFilter() {}

    public static boolean containsBannedWord(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return BANNED_WORDS.stream().anyMatch(lower::contains);
    }
}
