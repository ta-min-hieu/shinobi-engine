package dev.kage.server.gateway;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.ExpBuffFacade;
import dev.kage.protocol.ExpBuffChangedEvent;
import dev.kage.protocol.Opcode;

/** Đan x2 kinh nghiệm (ADR-164) — báo client thời gian x2 còn lại để HUD đếm ngược. */
final class ExpBuffCommands {

    private static final Logger LOG = LoggerFactory.getLogger(ExpBuffCommands.class);

    private final ExpBuffFacade facade;
    private final ConnectedCharacterRegistry registry;
    private final Clock clock;

    ExpBuffCommands(ExpBuffFacade facade, ConnectedCharacterRegistry registry, Clock clock) {
        this.facade = facade;
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * Đọc lại hạn (vừa dùng đan, hoặc vừa vào world) và gửi {@code ExpBuffChangedEvent} nếu còn hạn. Việc phụ — lỗi
     * dừng ở đây, không làm hỏng lệnh dùng vật phẩm hay việc vào world.
     */
    // PMD AvoidCatchingGenericException: việc phụ, không được làm hỏng đường chính.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void announce(long characterId) {
        try {
            facade.refresh(characterId);
            long secondsLeft = facade.secondsLeft(characterId, clock.instant());
            if (secondsLeft > 0) {
                registry.sendToCharacterIfOnline(
                        characterId,
                        Opcode.OPCODE_EXP_BUFF_CHANGED_EVENT_VALUE,
                        0,
                        ExpBuffChangedEvent.newBuilder()
                                .setSecondsLeft(secondsLeft)
                                .build()
                                .toByteArray());
            }
        } catch (RuntimeException e) {
            LOG.warn("Báo x2 EXP lỗi: characterId={}", characterId, e);
        }
    }
}
