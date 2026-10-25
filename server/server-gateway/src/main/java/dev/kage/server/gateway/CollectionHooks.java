package dev.kage.server.gateway;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.achievement.domain.BestiaryPort;

/**
 * Hai việc phụ lô 22 phần 2: cộng lần hạ vào sổ sưu tầm (ADR-108 D2 — bước riêng của đường thưởng hạ quái) và cộng điểm
 * danh vọng (ADR-109 D2 — sau nhiệm vụ ngày, truy nã, bang chiến). Ghi DB trên Virtual Thread riêng; lỗi dừng ở đây,
 * việc chính không suy suyển.
 */
final class CollectionHooks {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionHooks.class);

    /** Mở trang mới: báo người chơi + áp lại chỉ số ngay (thưởng cộng phẳng vào ống trang bị). */
    @FunctionalInterface
    interface UnlockListener {
        void onUnlocked(long characterId, BestiaryPort.Entry entry);
    }

    private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();
    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng khuôn BountyHooks.
    private volatile CollectionFacades facades = CollectionFacades.DISABLED;
    private volatile UnlockListener unlocks = (characterId, entry) -> {};

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(CollectionFacades realFacades, UnlockListener realUnlocks) {
        this.unlocks = realUnlocks;
        this.facades = realFacades;
    }

    /** Facade sổ sưu tầm/danh vọng đang nối. */
    CollectionFacades facades() {
        return facades;
    }

    /** Đã chạy trên Virtual Thread của đường thưởng hạ quái — ghi luôn, không đẩy tiếp. */
    // PMD AvoidCatchingGenericException: việc phụ — lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onMonsterKilled(long killerCharacterId, String monsterCode) {
        try {
            Optional<BestiaryPort.Entry> unlocked = facades.bestiary().recordKill(killerCharacterId, monsterCode);
            unlocked.ifPresent(entry -> unlocks.onUnlocked(killerCharacterId, entry));
        } catch (RuntimeException e) {
            LOG.warn("Ghi sổ sưu tầm lỗi: characterId={}, monster={}", killerCharacterId, monsterCode, e);
        }
    }

    /** Cộng điểm danh vọng. Không chặn người gọi. */
    void addReputation(long characterId, int points) {
        CollectionFacades target = facades;
        writers.execute(() -> addQuietly(target, characterId, points));
    }

    // PMD AvoidCatchingGenericException: việc phụ — lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void addQuietly(CollectionFacades target, long characterId, int points) {
        try {
            target.reputation().add(characterId, points);
        } catch (RuntimeException e) {
            LOG.warn("Cộng danh vọng lỗi: characterId={}, points={}", characterId, points, e);
        }
    }

    /** Áp lại chỉ số cho một người — nối sau (cần bảng định tuyến World). */
    static UnlockListener refreshing(LongConsumer refresh, PlayerNotices notices) {
        return (characterId, entry) -> {
            notices.systemLine(
                    characterId,
                    "Mở trang sưu tầm: %s (+%d %s). / Bestiary page unlocked: %s (+%d %s)."
                            .formatted(
                                    entry.monsterCode(),
                                    entry.bonusValue(),
                                    entry.bonusStat(),
                                    entry.monsterCode(),
                                    entry.bonusValue(),
                                    entry.bonusStat()));
            refresh.accept(characterId);
        };
    }

    /** Tắt server có trật tự — gọi TRƯỚC khi đóng pool DB. */
    void stop() {
        writers.shutdown();
    }
}
