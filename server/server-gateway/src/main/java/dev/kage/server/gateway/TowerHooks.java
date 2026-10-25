package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.TowerFloorClearedEvent;
import dev.kage.world.api.TowerFacade;
import dev.kage.world.domain.TowerPort;

/**
 * Trấn Yêu Tháp (ADR-113 D2) — việc lúc hạ hết quái một tầng: ghi tầng (chỉ tăng), gửi thư thưởng mốc lần đầu, báo
 * client {@code TowerFloorClearedEvent} TRƯỚC khi {@link DungeonInstanceRegistry} đưa người đó về world chính. Nối sau
 * khi DB đã dựng, cùng khuôn {@link CollectionHooks}.
 */
final class TowerHooks {

    private static final Logger LOG = LoggerFactory.getLogger(TowerHooks.class);

    /** Thứ việc này cần. */
    record Deps(TowerFacade tower, PlayerNotices notices, ConnectedCharacterRegistry registry) {}

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau.
    private volatile Optional<Deps> deps = Optional.empty();

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(Deps realDeps) {
        this.deps = Optional.of(realDeps);
    }

    /** Facade tháp đang nối (lệnh của client). */
    TowerFacade tower() {
        return deps.map(Deps::tower).orElse(TowerFacade.DISABLED);
    }

    /** Đã ở Virtual Thread (chạm DB). Lỗi dừng ở đây — đường về world chính vẫn chạy tiếp. */
    // PMD AvoidCatchingGenericException: việc phụ — lỗi không được giữ người chơi lại trong tháp.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onFloorCleared(long characterId, int floor) {
        Optional<Deps> current = deps;
        if (current.isEmpty()) {
            return;
        }
        Deps d = current.get();
        try {
            Optional<TowerPort.Reward> reward = d.tower().recordCleared(characterId, floor);
            reward.ifPresent(r -> d.notices().mail(characterId, mailOf(r)));
            TowerFloorClearedEvent event = TowerFloorClearedEvent.newBuilder()
                    .setFloor(floor)
                    .setBestFloor(d.tower().bestFloorOf(characterId))
                    .setRewarded(reward.isPresent())
                    .build();
            d.registry()
                    .sendToCharacterIfOnline(
                            characterId, Opcode.OPCODE_TOWER_FLOOR_CLEARED_EVENT_VALUE, 0, event.toByteArray());
        } catch (RuntimeException e) {
            LOG.warn("Ghi tầng tháp lỗi: characterId={}, floor={}", characterId, floor, e);
        }
    }

    private static PlayerNotices.SystemMail mailOf(TowerPort.Reward reward) {
        List<MailFacade.AttachmentRequest> items = reward.itemTemplateId() == 0L
                ? List.of()
                : List.of(new MailFacade.AttachmentRequest(reward.itemTemplateId(), reward.quantity()));
        return new PlayerNotices.SystemMail(
                "Trấn Yêu Tháp / Demon Tower",
                "Lần đầu qua tầng %d. / First clear of floor %d.".formatted(reward.floor(), reward.floor()),
                reward.gold(),
                items);
    }
}
