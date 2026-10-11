package dev.kage.guild.domain;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Luật tên bang (quyết định chủ dự án 2026-09-29): ĐÚNG luật tên nhân vật
 * ({@code dev.kage.character.domain.Character#validateNewName}) — 2-16 ký tự, chỉ chữ cái (mọi ngôn ngữ,
 * kể cả tiếng Việt có dấu dựng sẵn), chữ số và {@code _}; không cắt khoảng trắng, không có danh sách
 * từ cấm (tên nhân vật cũng chưa có — SECURITY.md §12).
 *
 * <p><b>Vì sao chép luật chứ không gọi thẳng.</b> feature-guild bị cấm phụ thuộc Gradle vào
 * feature-character (feature-guild/CLAUDE.md), và {@code shared-common} cấm business logic. Hai bản
 * được giữ khớp bằng {@code GuildNameParityTest} (server-gateway — module duy nhất thấy cả hai):
 * sửa một bên mà quên bên kia thì build đỏ.
 *
 * <p>CHỈ kiểm lúc TẠO bang — không đặt vào compact constructor của {@link Guild}: bang tạo trước
 * 2026-09-29 có thể mang tên dài tới 32 ký tự hoặc có dấu cách, và dựng lại chúng từ DB không được
 * nổ.
 */
public final class GuildName {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N}_]+$");

    private GuildName() {}

    /** {@code true} nếu {@code name} dùng được làm tên bang MỚI; {@code null} là không hợp lệ. */
    public static boolean isValid(String name) {
        return name != null
                && name.length() >= MIN_LENGTH
                && name.length() <= MAX_LENGTH
                && ALLOWED.matcher(name).matches()
                && Normalizer.isNormalized(name, Normalizer.Form.NFKC)
                && name.codePoints().noneMatch(GuildName::isInvisibleLetter);
    }

    /**
     * Bốn ký tự lấp chỗ Hangul — chữ cái trên giấy, khoảng trống trên màn hình (2026-10-11). Cùng với
     * kiểm NFKC ở trên (chặn chữ khổ toàn phần, chữ toán học) là bản chép của
     * {@code Character#validateNewName}; {@code GuildNameParityTest} giữ hai bản khớp nhau.
     */
    private static boolean isInvisibleLetter(int codePoint) {
        return codePoint == 0x115F || codePoint == 0x1160 || codePoint == 0x3164 || codePoint == 0xFFA0;
    }
}
