package dev.kage.server.gateway;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.domain.PositionSnapshot;
import dev.kage.world.api.WaypointFacade;
import dev.kage.world.domain.Waypoint;

/**
 * Mở điểm truyền tống khi đứng gần lần đầu (ADR-100 D2) — luồng nền mỗi {@link #PERIOD_SECONDS} giây xem vị trí world
 * chính của người đang online. Không chạm DB trừ khi có điểm mới (bộ nhớ đệm trong {@link WaypointFacade}).
 */
final class WaypointScheduler {

    static final long PERIOD_SECONDS = 5L;

    private static final Logger LOG = LoggerFactory.getLogger(WaypointScheduler.class);

    private final WaypointFacade waypoints;
    private final ConnectedCharacterRegistry registry;
    private final LongPredicate outsideMainWorld;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "waypoint-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    WaypointScheduler(WaypointFacade waypoints, ConnectedCharacterRegistry registry, LongPredicate outsideMainWorld) {
        this.waypoints = waypoints;
        this.registry = registry;
        this.outsideMainWorld = outsideMainWorld;
    }

    // FutureReturnValueIgnored: lỗi từng vòng tự bắt trong scan().
    @SuppressWarnings("FutureReturnValueIgnored")
    void start() {
        timer.scheduleWithFixedDelay(this::scan, PERIOD_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    void stop() {
        timer.shutdownNow();
    }

    // PMD AvoidCatchingGenericException: ranh giới task nền định kỳ — một vòng lỗi không được giết lịch.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void scan() {
        try {
            for (Map.Entry<Long, PositionSnapshot> entry :
                    registry.snapshotPositions().entrySet()) {
                long characterId = entry.getKey();
                if (outsideMainWorld.test(characterId)) {
                    continue;
                }
                List<Waypoint> opened =
                        waypoints.visit(characterId, entry.getValue().x());
                opened.forEach(w -> registry.sendToCharacterIfOnline(
                        characterId,
                        dev.kage.protocol.Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE,
                        0,
                        dev.kage.protocol.ChatMessageEvent.newBuilder()
                                .setChannel(dev.kage.protocol.ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                                .setSenderName("")
                                .setBody("Mở điểm truyền tống mới. / New waypoint unlocked: " + w.code())
                                .build()
                                .toByteArray()));
            }
        } catch (RuntimeException e) {
            LOG.warn("Quét điểm truyền tống lỗi", e);
        }
    }
}
