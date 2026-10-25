package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.world.api.DungeonQuotaFacade;

/**
 * Lượt Bí Cảnh mỗi ngày + thưởng lần đầu hạ boss (ADR-099) — phần {@link DungeonInstanceRegistry} cần, gom một chỗ để
 * lớp đó chỉ thêm một tham chiếu. Chạm DB — chỉ gọi trên Virtual Thread.
 */
final class DungeonRewards {

    private static final Logger LOG = LoggerFactory.getLogger(DungeonRewards.class);

    /** Không giới hạn lượt, không thưởng lần đầu — composition test. */
    static final DungeonRewards NONE =
            new DungeonRewards(DungeonQuotaFacade.UNLIMITED, (killerId, members) -> {}, characterId -> {});

    /** Boss chết: mở lô đấu giá bang (ADR-112). */
    @FunctionalInterface
    interface BossLotListener {
        void onBossKilled(long killerId, List<Long> members);
    }

    private final DungeonQuotaFacade quota;
    private final LongConsumer firstClearReward;
    private final BossLotListener bossLots;

    /**
     * @param bossLots mở lô đấu giá bang cho người ra đòn cuối
     * @param firstClearReward gửi thư thưởng lần đầu cho một nhân vật
     */
    DungeonRewards(DungeonQuotaFacade quota, BossLotListener bossLots, LongConsumer firstClearReward) {
        this.quota = quota;
        this.bossLots = bossLots;
        this.firstClearReward = firstClearReward;
    }

    /** Trừ lượt cho những người sắp được kéo vào; {@code true} nếu ai đó đã hết lượt (không trừ ai). */
    boolean anyExhausted(List<Long> characterIds, String dungeonCode) {
        Optional<Long> exhausted = quota.tryConsume(characterIds, dungeonCode);
        return exhausted.isPresent();
    }

    int entriesLeft(long characterId, String dungeonCode) {
        return quota.entriesLeft(characterId, dungeonCode);
    }

    /**
     * Boss vừa chết: ai trong {@code characterIds} hạ boss này lần đầu thì nhận thư; người ra đòn cuối có bang thì bang
     * được một lô đấu giá (ADR-112). Việc phụ — lỗi dừng ở đây.
     */
    // PMD AvoidCatchingGenericException: việc phụ trên luồng nền — một người lỗi không chặn người khác.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onBossKilled(long killerId, List<Long> characterIds, String dungeonCode) {
        bossLots.onBossKilled(killerId, characterIds);
        for (long characterId : characterIds) {
            try {
                if (quota.recordFirstClear(characterId, dungeonCode)) {
                    firstClearReward.accept(characterId);
                }
            } catch (RuntimeException e) {
                LOG.warn("Thưởng lần đầu Bí Cảnh lỗi — MẤT: characterId={}", characterId, e);
            }
        }
    }
}
