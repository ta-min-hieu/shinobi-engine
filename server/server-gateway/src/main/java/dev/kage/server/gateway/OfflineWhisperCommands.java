package dev.kage.server.gateway;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.chat.api.OfflineWhisperFacade;
import dev.kage.chat.domain.OfflineWhisperPort;
import dev.kage.friend.api.BlockFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendChatMessageOutcome;

/**
 * Nhắn riêng khi offline (ADR-187) — giữ tin riêng tới người đang offline (đã qua kiểm duyệt + audit của chat) và giao
 * khi họ vào game: mỗi tin một {@code ChatMessageEvent} kênh riêng mang {@code sent_at_epoch_seconds}. Người đã chặn
 * nhau từ lúc gửi tới lúc giao thì tin bị bỏ.
 */
final class OfflineWhisperCommands {

    private static final Logger LOG = LoggerFactory.getLogger(OfflineWhisperCommands.class);

    /** Nguồn dữ liệu. */
    record Deps(OfflineWhisperFacade whispers, BlockFacade blocks, ConnectedCharacterRegistry registry, Clock clock) {}

    private final Deps deps;

    OfflineWhisperCommands(Deps deps) {
        this.deps = deps;
    }

    /** Virtual Thread; giữ một tin đã kiểm duyệt cho người nhận đang offline. */
    SendChatMessageOutcome store(long senderId, long recipientId, String body) {
        if (deps.whispers() == OfflineWhisperFacade.DISABLED) {
            // Composition không có hộp tin chờ: giữ đúng hành vi cũ (ChatWireTest đóng đinh điều này).
            return SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_TARGET_OFFLINE;
        }
        return deps.whispers().store(senderId, recipientId, body, deps.clock().instant())
                ? SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_DELIVERED_LATER
                : SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_RECIPIENT_INBOX_FULL;
    }

    /**
     * Vào game: lấy ra (xoá) rồi giao mọi tin chờ còn hạn. Việc phụ — lỗi dừng ở đây, không làm hỏng lần vào game; lỗi
     * ở bước đọc DB thì transaction không commit nên tin còn nguyên cho lần vào sau.
     */
    // PMD AvoidCatchingGenericException: việc phụ lúc vào world, mọi lỗi cùng một cách xử lý (ghi WARN).
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void deliverOnEnter(long recipientId) {
        try {
            for (OfflineWhisperPort.Pending pending :
                    deps.whispers().takeAll(recipientId, deps.clock().instant())) {
                if (deps.blocks().eitherBlocks(recipientId, pending.senderId())) {
                    continue;
                }
                deps.registry()
                        .sendToCharacterIfOnline(
                                recipientId,
                                Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE,
                                0,
                                ChatMessageEvent.newBuilder()
                                        .setChannel(ChatChannel.CHAT_CHANNEL_PRIVATE)
                                        .setSenderId(pending.senderId())
                                        .setSenderName(pending.senderName())
                                        .setBody(pending.body())
                                        .setSentAtEpochSeconds(pending.sentAt().getEpochSecond())
                                        .build()
                                        .toByteArray());
            }
        } catch (RuntimeException e) {
            LOG.warn("Giao tin nhắn riêng chờ lỗi: recipientId={}", recipientId, e);
        }
    }
}
