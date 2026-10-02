package dev.kage.chat.domain;

import java.util.Objects;

/**
 * Value Object — 1 tin nhắn ĐÃ hợp lệ (độ dài, không rỗng). KHÔNG lưu DB (không aggregate/ID) —
 * chat không có lịch sử, chỉ audit_log giữ dấu vết (feature-chat/CLAUDE.md).
 */
public record ChatMessage(long senderId, ChatChannel channel, String body) {

    public static final int MAX_BODY_LENGTH = 200;

    public ChatMessage {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(body, "body");
        if (isVisuallyBlank(body)) {
            throw new IllegalArgumentException("body không được rỗng");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("body vượt quá " + MAX_BODY_LENGTH + " ký tự: " + body.length());
        }
    }

    /**
     * {@code true} nếu tin nhắn không có MỘT ký tự nhìn thấy được nào — rộng hơn
     * {@link String#isBlank()}: nó chỉ nhận khoảng trắng của {@link Character#isWhitespace}, nên
     * khoảng trắng không ngắt ({@code U+00A0}, {@code U+2007}, {@code U+202F}), ký tự độ rộng 0
     * ({@code U+200B}, {@code U+200D}, {@code U+FEFF}) và ký tự điều khiển lọt qua thành một dòng
     * chat trống phát đi cả server (sửa 2026-10-02).
     */
    public static boolean isVisuallyBlank(String body) {
        return body.codePoints().allMatch(ChatMessage::isInvisible);
    }

    private static boolean isInvisible(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isWhitespace(codePoint)
                || type == Character.SPACE_SEPARATOR
                || type == Character.CONTROL
                || type == Character.FORMAT;
    }
}
