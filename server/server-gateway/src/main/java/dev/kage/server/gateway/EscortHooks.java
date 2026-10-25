package dev.kage.server.gateway;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.EscortStateEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuestStatus;
import dev.kage.protocol.QuestStatusChangedEvent;
import dev.kage.quest.api.EscortRouteFacade;
import dev.kage.quest.api.QuestFacade;
import dev.kage.quest.domain.EscortRoutePort;
import dev.kage.server.world.EscortRoute;
import dev.kage.server.world.WorldFacade;
import dev.kage.server.world.WorldOutboundSink;

/**
 * Nhiệm vụ hộ tống (ADR-114) — nối feature-quest với đoàn hộ tống của server-world. Nhận quest có tuyến ⇒ xếp đoàn vào
 * World chính của người nhận; mỗi lần đoàn đổi (tick thread) ⇒ gửi 625 cho người hộ tống; đoàn kết thúc ⇒ trên Virtual
 * Thread riêng: tới nơi là sự kiện {@code escort_arrived}, vỡ/bỏ là {@code escort_failed} + bỏ quest về "chưa nhận"
 * (đường ADR-054) để nhận lại được. Nối sau khi DB đã dựng, cùng khuôn {@link CollectionHooks}.
 */
final class EscortHooks {

    private static final Logger LOG = LoggerFactory.getLogger(EscortHooks.class);

    /** Thứ việc này cần. */
    record Deps(
            QuestFacade quests, EscortRouteFacade routes, PlayerNotices notices, ConnectedCharacterRegistry registry) {}

    private final ExecutorService finishers = Executors.newVirtualThreadPerTaskExecutor();
    // volatile, không final: registry dựng TRƯỚC DB — bind() sau.
    private volatile Optional<Deps> deps = Optional.empty();
    /** Điểm đến của đoàn đang đi — chỉ để báo client (snapshot của World không mang nó). */
    private final Map<Long, Float> endXByCharacterId = new ConcurrentHashMap<>();
    /** Người tự bỏ quest — đoàn của họ kết thúc lặng lẽ, không báo "xe vỡ". */
    private final Set<Long> cancelledByPlayer = ConcurrentHashMap.newKeySet();

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(Deps realDeps) {
        this.deps = Optional.of(realDeps);
    }

    /** Vừa nhận quest {@code questCode} OK (Virtual Thread, chạm DB): quest có tuyến thì xếp đoàn vào {@code world}. */
    void onQuestAccepted(long characterId, String questCode, Optional<WorldFacade> world) {
        Optional<Deps> current = deps;
        if (current.isEmpty() || world.isEmpty()) {
            return;
        }
        Optional<EscortRoutePort.Route> route = current.get().routes().routeOf(questCode);
        route.ifPresent(r -> {
            endXByCharacterId.put(characterId, r.endX());
            world.get()
                    .startEscort(
                            characterId, new EscortRoute(r.questCode(), r.startX(), r.endX(), r.maxHp(), r.speedMps()));
        });
    }

    /** Người chơi tự bỏ quest: huỷ đoàn (nếu có) mà không báo "xe vỡ". */
    void onQuestAbandoned(long characterId, Optional<WorldFacade> world) {
        if (endXByCharacterId.containsKey(characterId)) {
            cancelledByPlayer.add(characterId);
            world.ifPresent(w -> w.cancelEscort(characterId));
        }
    }

    /** Tick thread — chỉ gửi gói (không chặn); việc DB lúc kết thúc đẩy sang Virtual Thread. */
    void onChanged(WorldOutboundSink.EscortSnapshot snapshot) {
        Optional<Deps> current = deps;
        if (current.isEmpty()) {
            return;
        }
        long characterId = snapshot.characterId();
        EscortStateEvent event = EscortStateEvent.newBuilder()
                .setQuestCode(snapshot.routeCode())
                .setX(snapshot.x())
                .setHp(snapshot.hp())
                .setMaxHp(snapshot.maxHp())
                .setState(snapshot.state().name())
                .setEndX(endXByCharacterId.getOrDefault(characterId, snapshot.x()))
                .build();
        current.get()
                .registry()
                .sendToCharacterIfOnline(characterId, Opcode.OPCODE_ESCORT_STATE_EVENT_VALUE, 0, event.toByteArray());
        if (isFinal(snapshot.state())) {
            endXByCharacterId.remove(characterId);
            Deps d = current.get();
            finishers.execute(() -> finish(d, snapshot));
        }
    }

    private static boolean isFinal(WorldOutboundSink.EscortState state) {
        return state == WorldOutboundSink.EscortState.ARRIVED
                || state == WorldOutboundSink.EscortState.FAILED
                || state == WorldOutboundSink.EscortState.ABANDONED;
    }

    // PMD AvoidCatchingGenericException: việc phụ trên luồng nền — lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void finish(Deps d, WorldOutboundSink.EscortSnapshot snapshot) {
        long characterId = snapshot.characterId();
        String questCode = snapshot.routeCode();
        try {
            if (snapshot.state() == WorldOutboundSink.EscortState.ARRIVED) {
                for (QuestFacade.QuestStatusChange change : d.quests().onEscortFinished(characterId, questCode, true)) {
                    pushCompleted(d, characterId, change.questCode());
                }
                d.notices().systemLine(characterId, "Xe linh thảo đã tới nơi! / The herb cart has arrived!");
                return;
            }
            d.quests().onEscortFinished(characterId, questCode, false);
            d.quests().abandonQuest(characterId, questCode);
            if (!cancelledByPlayer.remove(characterId)) {
                d.notices()
                        .systemLine(
                                characterId,
                                "Hộ tống thất bại — nhận lại nhiệm vụ ở Chu Hoành. / Escort failed — accept it again.");
            }
        } catch (RuntimeException e) {
            LOG.warn("Kết thúc hộ tống lỗi: characterId={}, quest={}", characterId, questCode, e);
        }
    }

    private static void pushCompleted(Deps d, long characterId, String questCode) {
        QuestStatusChangedEvent event = QuestStatusChangedEvent.newBuilder()
                .setQuestCode(questCode)
                .setStatus(QuestStatus.QUEST_STATUS_COMPLETED)
                .build();
        d.registry()
                .sendToCharacterIfOnline(
                        characterId, Opcode.OPCODE_QUEST_STATUS_CHANGED_EVENT_VALUE, 0, event.toByteArray());
    }

    /** Tắt server có trật tự — gọi TRƯỚC khi đóng pool DB. */
    void stop() {
        finishers.shutdown();
        ShutdownDrain.await(finishers, "Kết thúc hộ tống");
    }
}
