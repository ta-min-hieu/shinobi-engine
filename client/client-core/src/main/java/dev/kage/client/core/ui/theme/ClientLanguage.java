package dev.kage.client.core.ui.theme;

import java.util.Locale;
import java.util.Optional;

/**
 * Ngôn ngữ hiển thị người chơi chọn được — quyết định chủ dự án 2026-10-01: game <b>song ngữ
 * Việt–Anh từ MVP</b>.
 *
 * <p>Thuần client: chỉ đổi bundle {@code i18n/strings_<code>} mà {@link KageI18n#load()} nạp. Server
 * không biết người chơi đang đọc tiếng gì — nó chỉ gửi khoá ({@code error_key}, {@code name_key},
 * {@code text_key}), client tự dịch — nên đổi ngôn ngữ không đụng protocol hay DB.
 *
 * <p>Thêm ngôn ngữ mới = thêm một hằng ở đây + một file {@code strings_<code>.properties} + khai báo
 * nó trong {@code MessageBundleIntegrityTest.BUNDLES} và hai cổng build {@code verifyServerErrorKeys}/
 * {@code verifyContentKeys}.
 */
public enum ClientLanguage {
    VI("vi"),
    EN("en");

    /** Ngôn ngữ khi người chơi chưa từng chọn, hoặc giá trị đã lưu không đọc được. */
    public static final ClientLanguage DEFAULT = VI;

    private final String code;

    ClientLanguage(String code) {
        this.code = code;
    }

    /** Mã ISO 639-1 — cũng là hậu tố file bundle và là giá trị được lưu xuống máy người chơi. */
    public String code() {
        return code;
    }

    public Locale locale() {
        return Locale.of(code);
    }

    /**
     * Khoá i18n của TÊN ngôn ngữ này. Cả hai bundle ghi tên bằng chính ngôn ngữ đó ("Tiếng Việt",
     * "English") — người lỡ chọn nhầm sang thứ tiếng mình không đọc được vẫn nhận ra đường quay về.
     */
    public String nameKey() {
        return "language.name." + code;
    }

    /**
     * Tra ngôn ngữ theo mã đã lưu.
     *
     * @param code mã đọc từ nơi lưu, có thể {@code null}/rỗng/rác (file sửa tay, bản cũ hơn)
     * @return ngôn ngữ khớp mã (không phân biệt hoa thường, bỏ khoảng trắng hai đầu), hoặc rỗng
     */
    public static Optional<ClientLanguage> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.strip().toLowerCase(Locale.ROOT);
        for (ClientLanguage language : values()) {
            if (language.code.equals(normalized)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }
}
