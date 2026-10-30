package dev.kage.world.api;

import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;

import dev.kage.world.domain.SpiritFieldPort;
import dev.kage.world.domain.SpiritFieldRules;

/** Linh điền (ADR-119). Chạm DB — chỉ gọi trên Virtual Thread; {@code characterId} từ session. */
public final class SpiritFieldFacade {

    /** Composition không có linh điền (test). */
    public static final SpiritFieldFacade DISABLED = new SpiritFieldFacade(new SpiritFieldPort() {
        @Override
        public List<Seed> seeds() {
            return List.of();
        }

        @Override
        public Field field(long characterId) {
            return new Field(SpiritFieldRules.STARTING_PLOTS, List.of());
        }

        @Override
        public Outcome plant(long characterId, int plotIndex, long seedTemplateId, Instant now) {
            return Outcome.UNKNOWN_SEED;
        }

        @Override
        public Outcome harvest(
                long characterId,
                int plotIndex,
                Instant now,
                java.util.function.BiFunction<Integer, Boolean, Integer> yieldOf) {
            return Outcome.PLOT_EMPTY;
        }

        @Override
        public Outcome water(long waterCharacterId, long ownerId, int plotIndex, Instant now) {
            return Outcome.PLOT_EMPTY;
        }

        @Override
        public Outcome unlockPlot(long characterId) {
            return Outcome.MAX_PLOTS;
        }
    });

    private final SpiritFieldPort port;
    private final SplittableRandom random = new SplittableRandom();

    /** Dựng ở composition root. */
    public SpiritFieldFacade(SpiritFieldPort port) {
        this.port = port;
    }

    /** Các loại hạt giống. */
    public List<SpiritFieldPort.Seed> seeds() {
        return port.seeds();
    }

    /** Ruộng của một người (của mình, hoặc của bạn để tưới). */
    public SpiritFieldPort.Field field(long characterId) {
        return port.field(characterId);
    }

    /** Gieo một hạt từ túi vào ô trống. */
    public SpiritFieldPort.Outcome plant(long characterId, int plotIndex, long seedTemplateId, Instant now) {
        return port.plant(characterId, plotIndex, seedTemplateId, now);
    }

    /** Thu hoạch ô đã chín. */
    public SpiritFieldPort.Outcome harvest(long characterId, int plotIndex, Instant now) {
        return port.harvest(characterId, plotIndex, now, (base, watered) -> {
            synchronized (random) {
                return SpiritFieldRules.harvestYield(base, watered, random);
            }
        });
    }

    /** Tưới ô của bạn — người gọi đã kiểm hai người là bạn bè. */
    public SpiritFieldPort.Outcome water(long waterCharacterId, long ownerId, int plotIndex, Instant now) {
        return port.water(waterCharacterId, ownerId, plotIndex, now);
    }

    /** Mở thêm một ô bằng linh thạch. */
    public SpiritFieldPort.Outcome unlockPlot(long characterId) {
        return port.unlockPlot(characterId);
    }
}
