package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.chat.api.ChatFacade;
import dev.kage.chat.domain.ChatChannel;
import dev.kage.chat.domain.ChatMessage;
import dev.kage.protocol.BroadcastMessageCommand;
import dev.kage.protocol.BroadcastMessageOutcome;
import dev.kage.protocol.BroadcastMessageResponse;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.world.api.ConsumableFacade;

/**
 * Truyền Âm Phù (ADR-152) — loa thế giới. Thứ tự: hồi chiêu {@value #COOLDOWN_SECONDS} giây (RAM) → khoá chat → tiêu
 * một phù → kiểm nội dung + audit qua ĐÚNG đường chat ({@link ChatFacade#sendMessage}, kênh {@code BROADCAST}) — bị từ
 * chối thì TRẢ phù → phát cho mọi người online. Lệnh chat thường không gửi được vào kênh này (gateway coi là kênh lạ).
 */
final class BroadcastCommands {

    static final long COOLDOWN_SECONDS = 30L;

    /** Mã vật phẩm Truyền Âm Phù (V123). */
    static final String TALISMAN_CODE = "broadcast_talisman";

    /** Thứ việc này cần. */
    record Deps(
            ChatFacade chat,
            CharacterFacade characters,
            ConsumableFacade consumables,
            ConnectedCharacterRegistry registry,
            Clock clock) {}

    private final Deps deps;
    private final Map<Long, Instant> lastSent = new ConcurrentHashMap<>();

    BroadcastCommands(Deps deps) {
        this.deps = deps;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    BroadcastMessageResponse broadcast(long characterId, BroadcastMessageCommand command) {
        Instant now = deps.clock().instant();
        Instant last = lastSent.get(characterId);
        if (last != null) {
            long left =
                    Duration.between(now, last.plusSeconds(COOLDOWN_SECONDS)).toSeconds();
            if (left > 0) {
                return reply(BroadcastMessageOutcome.BROADCAST_MESSAGE_OUTCOME_COOLDOWN, (int) left);
            }
        }
        Optional<Character> sender = deps.characters().findById(characterId);
        if (sender.isEmpty() || deps.chat().isMuted(sender.get().accountId(), now)) {
            return reply(BroadcastMessageOutcome.BROADCAST_MESSAGE_OUTCOME_MUTED, 0);
        }
        String body = ChatMessage.singleLine(command.getBody());
        if (!deps.consumables().consume(characterId, TALISMAN_CODE)) {
            return reply(BroadcastMessageOutcome.BROADCAST_MESSAGE_OUTCOME_NO_TALISMAN, 0);
        }
        ChatFacade.SendResult checked = deps.chat().sendMessage(characterId, ChatChannel.BROADCAST, body);
        if (checked != ChatFacade.SendResult.OK) {
            deps.consumables().refund(characterId, TALISMAN_CODE);
            return reply(wire(checked), 0);
        }
        lastSent.put(characterId, now);
        ChatMessageEvent event = ChatMessageEvent.newBuilder()
                .setChannel(dev.kage.protocol.ChatChannel.CHAT_CHANNEL_BROADCAST)
                .setSenderId(characterId)
                .setSenderName(sender.get().name())
                .setBody(body)
                .setSenderTitleKey(deps.registry().achievements.titleKeyOf(characterId))
                .build();
        deps.registry().broadcastToAll(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, event.toByteArray());
        return reply(BroadcastMessageOutcome.BROADCAST_MESSAGE_OUTCOME_OK, (int) COOLDOWN_SECONDS);
    }

    private static BroadcastMessageResponse reply(BroadcastMessageOutcome outcome, int secondsLeft) {
        return BroadcastMessageResponse.newBuilder()
                .setOutcome(outcome)
                .setSecondsLeft(secondsLeft)
                .build();
    }

    private static BroadcastMessageOutcome wire(ChatFacade.SendResult result) {
        return switch (result) {
            case OK -> BroadcastMessageOutcome.BROADCAST_MESSAGE_OUTCOME_OK;
            case MESSAGE_BLANK -> BroadcastMessageOutcome.BROADCAST_MESSAGE_OUTCOME_BLANK;
            case MESSAGE_TOO_LONG -> BroadcastMessageOutcome.BROADCAST_MESSAGE_OUTCOME_TOO_LONG;
            case CONTAINS_BANNED_WORD -> BroadcastMessageOutcome.BROADCAST_MESSAGE_OUTCOME_BANNED_WORD;
        };
    }
}
