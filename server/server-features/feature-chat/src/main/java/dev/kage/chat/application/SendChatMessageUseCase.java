package dev.kage.chat.application;

import java.util.OptionalLong;

import dev.kage.chat.domain.ChatAuditPort;
import dev.kage.chat.domain.ChatChannel;
import dev.kage.chat.domain.ChatMessage;
import dev.kage.chat.domain.ProfanityFilter;

/**
 * Validate + ghi audit cho 1 tin nhắn — KHÔNG định tuyến người nhận (World/Riêng tư/Khu vực là
 * việc của gateway/server-world, xem feature-chat/CLAUDE.md — cùng cách {@code AttackResultEvent}
 * phát hiện nay, feature không tự biết ai đang kết nối).
 */
public final class SendChatMessageUseCase {

    private final ChatAuditPort auditPort;

    public SendChatMessageUseCase(ChatAuditPort auditPort) {
        this.auditPort = auditPort;
    }

    /** Như {@link #execute(long, ChatChannel, String, OptionalLong)}, không có người nhận riêng. */
    public Result execute(long senderId, ChatChannel channel, String body) {
        return execute(senderId, channel, body, OptionalLong.empty());
    }

    /**
     * Validate rồi ghi audit một tin.
     *
     * @param recipientId người nhận tin RIÊNG ({@link ChatChannel#PRIVATE}) — ghi vào audit; rỗng với kênh khác
     */
    public Result execute(long senderId, ChatChannel channel, String body, OptionalLong recipientId) {
        if (body == null || ChatMessage.isVisuallyBlank(body)) {
            return Result.MESSAGE_BLANK;
        }
        if (body.length() > ChatMessage.MAX_BODY_LENGTH) {
            return Result.MESSAGE_TOO_LONG;
        }
        if (ProfanityFilter.containsBannedWord(body)) {
            return Result.CONTAINS_BANNED_WORD;
        }
        ChatMessage message = new ChatMessage(senderId, channel, body);
        auditPort.log(senderId, "CHAT_MESSAGE", auditDetail(message, recipientId));
        return Result.OK;
    }

    /**
     * Kênh + người nhận (tin riêng) + NỘI DUNG tin (dòng 71(e), chủ dự án duyệt 2026-10-17) — trước đó
     * chỉ có kênh, nên một báo cáo quấy rối không có gì để đối chiếu. Dữ liệu riêng tư: server-database
     * xoá các dòng này sau 30 ngày ({@code RetentionJanitor}). Nội dung là chuỗi người dùng ⇒ thoát JSON.
     */
    static String auditDetail(ChatMessage message, OptionalLong recipientId) {
        StringBuilder json = new StringBuilder(64 + message.body().length());
        json.append("{\"channel\":\"").append(message.channel()).append('"');
        recipientId.ifPresent(id -> json.append(",\"recipientId\":").append(id));
        json.append(",\"body\":\"");
        appendJsonEscaped(json, message.body());
        return json.append("\"}").toString();
    }

    private static void appendJsonEscaped(StringBuilder json, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                json.append('\\').append(c);
            } else if (c < ' ') {
                json.append("\\u%04x".formatted((int) c));
            } else {
                json.append(c);
            }
        }
    }

    public enum Result {
        OK,
        MESSAGE_BLANK,
        MESSAGE_TOO_LONG,
        CONTAINS_BANNED_WORD
    }
}
