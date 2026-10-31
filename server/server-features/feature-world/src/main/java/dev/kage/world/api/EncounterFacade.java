package dev.kage.world.api;

import java.time.Instant;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.ToIntFunction;

import dev.kage.world.domain.EncounterPort;
import dev.kage.world.domain.EncounterRules;

/** Kỳ ngộ (ADR-121). Chạm DB — chỉ gọi trên Virtual Thread; {@code characterId} từ session. */
public final class EncounterFacade {

    /** Composition không có kỳ ngộ (test) — không bao giờ mở. */
    public static final EncounterFacade DISABLED = new EncounterFacade(new EncounterPort() {
        @Override
        public Optional<Offer> tryStart(long characterId, Instant now, Instant dayStart, ToIntFunction<int[]> pick) {
            return Optional.empty();
        }

        @Override
        public Resolution choose(long characterId, long instanceId, int optionIndex, Instant now) {
            return Resolution.refused(Outcome.NOT_FOUND);
        }
    });

    private final EncounterPort port;
    private final SplittableRandom random = new SplittableRandom();

    /** Dựng ở composition root. */
    public EncounterFacade(EncounterPort port) {
        this.port = port;
    }

    /**
     * Sau một lượt hạ quái ở world chính: tung {@value EncounterRules#TRIGGER_PERCENT}% TRƯỚC (không chạm DB khi
     * trượt), trúng thì thử mở trong giao dịch (trần ngày, một kỳ ngộ chờ).
     */
    public Optional<EncounterPort.Offer> onMonsterKilled(long characterId, Instant now) {
        boolean hit;
        synchronized (random) {
            hit = EncounterRules.triggers(random);
        }
        return hit ? start(characterId, now) : Optional.empty();
    }

    /** Mở thẳng (bỏ qua tung xác suất) — test và lệnh GM. */
    public Optional<EncounterPort.Offer> start(long characterId, Instant now) {
        return port.tryStart(characterId, now, EncounterRules.dayStart(now), weights -> {
            synchronized (random) {
                return EncounterRules.pickWeighted(weights, random);
            }
        });
    }

    /** Chọn một lựa chọn. */
    public EncounterPort.Resolution choose(long characterId, long instanceId, int optionIndex, Instant now) {
        return port.choose(characterId, instanceId, optionIndex, now);
    }
}
