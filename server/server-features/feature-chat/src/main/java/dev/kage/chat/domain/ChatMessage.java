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

    /**
     * Ép tin nhắn về MỘT dòng (quyết định chủ dự án 2026-10-11): mọi ký tự xuống dòng và ký tự điều
     * khiển thành một dấu cách. Trước đó tin chứa xuống dòng hiện ra như hai dòng trong khung chat của
     * người nhận — dòng thứ hai trông như một tin KHÁC, kể cả tin hệ thống ("[Hệ thống] ...").
     *
     * <p>Gateway gọi hàm này TRƯỚC khi kiểm và phát: thứ được kiểm (độ dài, từ cấm) phải đúng là thứ
     * người khác nhận. Thay bằng dấu cách chứ không xoá, để hai từ hai bên chỗ ngắt không dính vào nhau.
     */
    public static String singleLine(String body) {
        if (body.codePoints().noneMatch(ChatMessage::breaksTheLine)) {
            return body;
        }
        StringBuilder line = new StringBuilder(body.length());
        body.codePoints().forEach(codePoint -> line.appendCodePoint(breaksTheLine(codePoint) ? ' ' : codePoint));
        return line.toString();
    }

    /** Ký tự điều khiển (gồm CR/LF/TAB) và hai dấu ngắt dòng/đoạn của Unicode (U+2028, U+2029). */
    private static boolean breaksTheLine(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static boolean isInvisible(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isWhitespace(codePoint)
                || type == Character.SPACE_SEPARATOR
                || type == Character.CONTROL
                || type == Character.FORMAT;
    }
}
