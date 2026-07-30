package dev.kage.chat.api;

import dev.kage.chat.application.SendChatMessageUseCase;
import dev.kage.chat.domain.ChatChannel;

/**
 * API công khai của {@code feature-chat} — module khác (server-gateway) CHỈ gọi qua đây. Facade
 * này KHÔNG định tuyến người nhận (xem javadoc {@link SendChatMessageUseCase}) — chỉ validate +
 * ghi audit, trả kết quả để gateway quyết định có phát tin đi hay không.
 */
public final class ChatFacade {

    private final SendChatMessageUseCase sendChatMessageUseCase;

    public ChatFacade(SendChatMessageUseCase sendChatMessageUseCase) {
        this.sendChatMessageUseCase = sendChatMessageUseCase;
    }

    public SendResult sendMessage(long senderId, ChatChannel channel, String body) {
        return switch (sendChatMessageUseCase.execute(senderId, channel, body)) {
            case OK -> SendResult.OK;
            case MESSAGE_BLANK -> SendResult.MESSAGE_BLANK;
            case MESSAGE_TOO_LONG -> SendResult.MESSAGE_TOO_LONG;
            case CONTAINS_BANNED_WORD -> SendResult.CONTAINS_BANNED_WORD;
        };
    }

    public enum SendResult {
        OK,
        MESSAGE_BLANK,
        MESSAGE_TOO_LONG,
        CONTAINS_BANNED_WORD
    }
}
