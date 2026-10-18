package dev.kage.chat.api;

import java.time.Instant;
import java.util.OptionalLong;

import dev.kage.chat.application.SendChatMessageUseCase;
import dev.kage.chat.domain.ChatChannel;
import dev.kage.chat.domain.ChatMutePort;

/**
 * API công khai của {@code feature-chat} — module khác (server-gateway) CHỈ gọi qua đây. Facade
 * này KHÔNG định tuyến người nhận (xem javadoc {@link SendChatMessageUseCase}) — chỉ validate +
 * ghi audit, trả kết quả để gateway quyết định có phát tin đi hay không.
 */
public final class ChatFacade {

    private final SendChatMessageUseCase sendChatMessageUseCase;
    private final ChatMutePort mutePort;

    /** Dựng ở composition root — không khoá chat (test cũ). */
    public ChatFacade(SendChatMessageUseCase sendChatMessageUseCase) {
        this(sendChatMessageUseCase, ChatMutePort.NONE);
    }

    /** {@code mutePort}: khoá chat của GM (ADR-052 D4). */
    public ChatFacade(SendChatMessageUseCase sendChatMessageUseCase, ChatMutePort mutePort) {
        this.sendChatMessageUseCase = sendChatMessageUseCase;
        this.mutePort = mutePort;
    }

    /** Tài khoản đang bị khoá chat tại thời điểm {@code now} không (dòng hết hạn = không). */
    public boolean isMuted(long accountId, Instant now) {
        return mutePort.mutedUntil(accountId).map(until -> until.isAfter(now)).orElse(false);
    }

    /** Khoá chat tài khoản tới {@code until} (ADR-052 — chỉ GmCommandRouter gọi). */
    public void mute(long accountId, Instant until, long gmAuditLogId) {
        mutePort.mute(accountId, until, gmAuditLogId);
    }

    /** Mở khoá chat; trả {@code true} nếu tài khoản đã bị khoá và nay được mở. */
    public boolean unmute(long accountId) {
        return mutePort.unmute(accountId);
    }

    /** Validate + ghi audit một tin không có người nhận riêng — gateway chỉ phát tin đi khi kết quả là {@code OK}. */
    public SendResult sendMessage(long senderId, ChatChannel channel, String body) {
        return sendMessage(senderId, channel, body, OptionalLong.empty());
    }

    /**
     * Như {@link #sendMessage(long, ChatChannel, String)}, kèm người nhận tin riêng để ghi audit (dòng 71(e)).
     *
     * @param recipientId người nhận tin riêng; rỗng với kênh khác
     */
    public SendResult sendMessage(long senderId, ChatChannel channel, String body, OptionalLong recipientId) {
        return switch (sendChatMessageUseCase.execute(senderId, channel, body, recipientId)) {
            case OK -> SendResult.OK;
            case MESSAGE_BLANK -> SendResult.MESSAGE_BLANK;
            case MESSAGE_TOO_LONG -> SendResult.MESSAGE_TOO_LONG;
            case CONTAINS_BANNED_WORD -> SendResult.CONTAINS_BANNED_WORD;
        };
    }

    /** Kết quả kiểm duyệt một tin; mọi giá trị khác {@code OK} nghĩa là tin KHÔNG được phát. */
    public enum SendResult {
        OK,
        MESSAGE_BLANK,
        MESSAGE_TOO_LONG,
        CONTAINS_BANNED_WORD
    }
}
