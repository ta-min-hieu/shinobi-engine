package dev.kage.world.api;

import java.time.Instant;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.ToIntFunction;

import dev.kage.world.domain.EncounterRules;
import dev.kage.world.domain.TreasurePort;

/**
 * Tàng Bảo Đồ (ADR-147). Chạm DB — chỉ gọi trên Virtual Thread; {@code characterId} từ session. Mọi lượt tung (điểm
 * chôn, phần thưởng) sinh ở server.
 */
public final class TreasureFacade {

    /** Composition không có Tàng Bảo Đồ (test) — không có cuộc tìm, không đọc được bản đồ. */
    public static final TreasureFacade DISABLED = new TreasureFacade(new TreasurePort() {
        @Override
        public Optional<Spot> activeHunt(long characterId) {
            return Optional.empty();
        }

        @Override
        public ReadResult read(long characterId, Instant now, ToIntFunction<int[]> pickSpot) {
            return new ReadResult(Outcome.NO_MAP, Optional.empty());
        }

        @Override
        public DigResult dig(long characterId, long expectedSpotId, Instant now, ToIntFunction<int[]> pickReward) {
            return DigResult.refused(Outcome.NO_HUNT, Optional.empty());
        }
    });

    private final TreasurePort port;
    private final SplittableRandom random = new SplittableRandom();

    /** Dựng ở composition root. */
    public TreasureFacade(TreasurePort port) {
        this.port = port;
    }

    /** Cuộc tìm đang mở. */
    public Optional<TreasurePort.Spot> activeHunt(long characterId) {
        return port.activeHunt(characterId);
    }

    /** Đọc một tấm bản đồ — điểm chôn chọn đều. */
    public TreasurePort.ReadResult read(long characterId, Instant now) {
        return port.read(characterId, now, this::pick);
    }

    /** Đào ở điểm {@code expectedSpotId} — phần thưởng theo trọng số. */
    public TreasurePort.DigResult dig(long characterId, long expectedSpotId, Instant now) {
        return port.dig(characterId, expectedSpotId, now, this::pick);
    }

    private int pick(int[] weights) {
        synchronized (random) {
            return EncounterRules.pickWeighted(weights, random);
        }
    }
}
