package dev.kage.server.gateway;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;

/**
 * Ghi vị trí nhân vật xuống DB ĐỊNH KỲ + lúc logout (SERVER.md §8) — KHÔNG mỗi tick, KHÔNG trên
 * tick thread/EventLoop (đây là thread nền riêng của gateway, tách khỏi cả hai).
 *
 * <p>Quét toàn bộ {@code ConnectedCharacterRegistry.snapshotPositions()} mỗi {@code interval} —
 * ở quy mô dự án cá nhân (vài người chơi), chạy tuần tự trực tiếp trên thread định kỳ là đơn
 * giản và đủ tốt; không cần tách sang Virtual Thread riêng như DB call trên EventLoop/tick thread
 * (SERVER.md §2 chỉ cấm 2 thread ĐÓ, không cấm thread nền tự quản của gateway).
 */
final class CharacterPositionPersister {

    private static final Logger LOG = LoggerFactory.getLogger(CharacterPositionPersister.class);

    private final CharacterFacade characterFacade;
    private final ConnectedCharacterRegistry registry;
    private final Duration interval;
    private ScheduledExecutorService scheduler;

    CharacterPositionPersister(
            CharacterFacade characterFacade, ConnectedCharacterRegistry registry, Duration interval) {
        this.characterFacade = characterFacade;
        this.registry = registry;
        this.interval = interval;
    }

    void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "character-position-persister"));
        long intervalMillis = interval.toMillis();
        scheduler.scheduleAtFixedRate(
                this::saveAllKnownPositions, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * Lưu NGAY 1 vị trí đã biết — dùng lúc logout (SERVER.md §8). Nhận thẳng {@code x,y} thay vì
     * tự tra {@code registry} vì caller ({@code channelInactive}) PHẢI đọc vị trí TRƯỚC khi gọi
     * {@code registry.unregister()} (unregister xoá luôn khỏi {@code lastKnownPosition} — tra lại
     * ở đây, chạy trễ hơn trên {@code authExecutor}, có thể đã bị xoá mất, đọc ra {@code null}).
     */
    void save(long characterId, float x, float y) {
        savePositionSafely(characterId, x, y);
    }

    // PMD AvoidCatchingGenericException: task nền định kỳ — 1 nhân vật lỗi (DB tạm gián đoạn)
    // không được làm cả vòng quét dừng lại hay ScheduledExecutorService tự huỷ lịch tiếp theo.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void saveAllKnownPositions() {
        registry.snapshotPositions()
                .forEach((characterId, position) -> savePositionSafely(characterId, position.x(), position.y()));
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void savePositionSafely(long characterId, float x, float y) {
        try {
            characterFacade.savePosition(characterId, x, y);
        } catch (RuntimeException e) {
            LOG.warn("Lưu vị trí thất bại: characterId={}", characterId, e);
        }
    }
}
