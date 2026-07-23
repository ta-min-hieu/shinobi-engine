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
        if (body.isBlank()) {
            throw new IllegalArgumentException("body không được rỗng");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "body vượt quá " + MAX_BODY_LENGTH + " ký tự: " + body.length());
        }
    }
}
