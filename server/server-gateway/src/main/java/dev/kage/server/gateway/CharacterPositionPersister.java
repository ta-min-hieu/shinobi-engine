package dev.kage.server.gateway;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
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
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

    private final PositionWriter writer;
    private final ConnectedCharacterRegistry registry;
    private final Duration interval;
    // volatile: start() chạy trên main thread, save() trên Virtual Thread của authExecutor.
    private volatile ScheduledExecutorService scheduler;

    CharacterPositionPersister(
            CharacterFacade characterFacade, ConnectedCharacterRegistry registry, Duration interval) {
        this(characterFacade::savePosition, registry, interval);
    }

    /** Seam cho test: thay đích ghi DB bằng một writer điều khiển được (chặn/nhả bằng latch). */
    CharacterPositionPersister(PositionWriter writer, ConnectedCharacterRegistry registry, Duration interval) {
        this.writer = writer;
        this.registry = registry;
        this.interval = interval;
    }

    /** Đích ghi một vị trí — production là {@code CharacterFacade::savePosition} (blocking, chạm DB). */
    @FunctionalInterface
    interface PositionWriter {
        void savePosition(long characterId, float x, float y);
    }

    void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "character-position-persister"));
        long intervalMillis = interval.toMillis();
        scheduler.scheduleAtFixedRate(
                this::saveAllKnownPositions, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Dừng lịch định kỳ, nhưng vẫn chạy nốt mọi lượt ghi logout ĐÃ xếp hàng (mặc định của
     * {@code ScheduledThreadPoolExecutor}: task không trễ còn trong hàng vẫn chạy sau
     * {@code shutdown()}), rồi chờ có giới hạn để lượt đang ghi dở kết thúc trước khi DB đóng.
     */
    void stop() {
        ScheduledExecutorService current = scheduler;
        if (current == null) {
            return;
        }
        current.shutdown();
        try {
            if (!current.awaitTermination(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warn("Lưu vị trí chưa xong sau {} — có thể mất vị trí mới nhất của vài nhân vật", STOP_TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Lưu 1 vị trí đã biết — dùng lúc logout (SERVER.md §8). Nhận thẳng {@code x,y} thay vì
     * tự tra {@code registry} vì caller ({@code channelInactive}) PHẢI đọc vị trí TRƯỚC khi gọi
     * {@code registry.unregister()} (unregister xoá luôn khỏi {@code lastKnownPosition} — tra lại
     * ở đây, chạy trễ hơn trên {@code authExecutor}, có thể đã bị xoá mất, đọc ra {@code null}).
     *
     * <p><b>Xếp hàng vào CÙNG thread với lượt định kỳ, không ghi ngay tại chỗ.</b> Lượt định kỳ chụp
     * {@code snapshotPositions()} rồi mới ghi từng nhân vật — nếu nó chụp vị trí CŨ, rồi nhân vật đi
     * tiếp và thoát, lần ghi logout (vị trí MỚI) chạy song song trên {@code authExecutor} có thể
     * xong TRƯỚC lần ghi cũ đang dở, và lần ghi cũ đè lên: đăng nhập lại bị lùi vài bước. Xếp sau
     * trên executor một-thread thì lượt đang chạy (lượt duy nhất có thể đã chụp vị trí cũ hơn lúc
     * caller đọc) xong trước; lượt nào bắt đầu SAU lời gọi này thì nhân vật đã bị
     * {@code unregister()}, không còn trong ảnh chụp. Chưa {@link #start()} (test) hoặc đã
     * {@link #stop()} (tắt server — đã chờ lượt đang dở xong) thì ghi ngay trên thread gọi.
     */
    void save(long characterId, float x, float y) {
        ScheduledExecutorService current = scheduler;
        if (current != null) {
            try {
                current.execute(() -> savePositionSafely(characterId, x, y));
                return;
            } catch (RejectedExecutionException e) {
                LOG.debug("Persister đã dừng — ghi vị trí trực tiếp: characterId={}", characterId, e);
            }
        }
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
            writer.savePosition(characterId, x, y);
        } catch (RuntimeException e) {
            LOG.warn("Lưu vị trí thất bại: characterId={}", characterId, e);
        }
    }
}
