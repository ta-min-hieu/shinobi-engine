package dev.kage.server.gateway;

import java.util.Optional;
import java.util.function.LongConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.gm.api.GameEventFacade;
import dev.kage.gm.domain.GameEventKind;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.PetProgress;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;

/**
 * Linh thú đang mặc nhận EXP như chủ nhân khi hạ quái (ADR-072 D2). Lên cấp ⇒ tính lại chỉ số trong World đang
 * đứng + một dòng hệ thống. Chạm DB: chỉ gọi trên Virtual Thread. Việc PHỤ: lỗi dừng ở đây (log WARN).
 */
final class PetHooks {

    /** Gửi một gói cho một nhân vật nếu đang online. */
    @FunctionalInterface
    interface Sink {
        void send(long characterId, int opcode, byte[] payload);
    }

    private static final Logger LOG = LoggerFactory.getLogger(PetHooks.class);

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng lý do AchievementHooks.
    private volatile Optional<InventoryFacade> inventory = Optional.empty();
    private volatile GameEventFacade events = GameEventFacade.DISABLED;
    private volatile LongConsumer refreshStats = characterId -> {};
    private volatile Sink sink = (id, opcode, payload) -> {};

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(InventoryFacade realInventory, GameEventFacade realEvents, LongConsumer realRefresh, Sink realSink) {
        this.inventory = Optional.of(realInventory);
        this.events = realEvents;
        this.refreshStats = realRefresh;
        this.sink = realSink;
    }

    /** {@code amount}: EXP gốc của con quái — nhân sự kiện ở đây, cùng hệ số chủ nhân nhận (ADR-067). */
    // PMD AvoidCatchingGenericException: việc phụ — mọi lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onExperience(long characterId, long amount) {
        if (amount <= 0L || inventory.isEmpty()) {
            return;
        }
        try {
            inventory
                    .get()
                    .grantPetExp(characterId, amount * events.multiplier(GameEventKind.EXP))
                    .filter(PetProgress::leveledUp)
                    .ifPresent(progress -> onLevelUp(characterId, progress));
        } catch (RuntimeException e) {
            LOG.warn("Cộng EXP linh thú lỗi: characterId={}", characterId, e);
        }
    }

    private void onLevelUp(long characterId, PetProgress progress) {
        refreshStats.accept(characterId);
        ChatMessageEvent notice = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody("Linh thú lên cấp " + progress.level() + "! / Your spirit pet reached level "
                        + progress.level() + "!")
                .build();
        sink.send(characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, notice.toByteArray());
    }
}
