package dev.kage.chat.application;

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

    public Result execute(long senderId, ChatChannel channel, String body) {
        if (body == null || body.isBlank()) {
            return Result.MESSAGE_BLANK;
        }
        if (body.length() > ChatMessage.MAX_BODY_LENGTH) {
            return Result.MESSAGE_TOO_LONG;
        }
        if (ProfanityFilter.containsBannedWord(body)) {
            return Result.CONTAINS_BANNED_WORD;
        }
        ChatMessage message = new ChatMessage(senderId, channel, body);
        String detail = "{\"channel\":\"%s\"}".formatted(message.channel());
        auditPort.log(senderId, "CHAT_MESSAGE", detail);
        return Result.OK;
    }

    public enum Result {
        OK,
        MESSAGE_BLANK,
        MESSAGE_TOO_LONG,
        CONTAINS_BANNED_WORD
    }
}
