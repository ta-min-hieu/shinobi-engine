package dev.kage.inventory.api;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.ToIntFunction;

import dev.kage.inventory.domain.LuckyBagPort;

/** Túi phúc (ADR-163). Chạm DB — chỉ gọi trên Virtual Thread. Random sinh ở server. */
public final class LuckyBagFacade {

    /** Composition không có túi phúc (test). */
    public static final LuckyBagFacade DISABLED = new LuckyBagFacade(new LuckyBagPort() {
        @Override
        public List<Bag> bags(long characterId) {
            return List.of();
        }

        @Override
        public Opened open(long characterId, long bagItemTemplateId, ToIntFunction<int[]> pick) {
            return Opened.refused(Outcome.NOT_A_BAG);
        }
    });

    private final LuckyBagPort port;
    private final SplittableRandom random = new SplittableRandom();

    /** Dựng ở composition root. */
    public LuckyBagFacade(LuckyBagPort port) {
        this.port = port;
    }

    /** Bảng túi phúc kèm số túi người đó có. */
    public List<LuckyBagPort.Bag> bags(long characterId) {
        return port.bags(characterId);
    }

    /** Mở một túi — server rút theo trọng số. */
    public LuckyBagPort.Opened open(long characterId, long bagItemTemplateId) {
        return port.open(characterId, bagItemTemplateId, this::pick);
    }

    private int pick(int[] weights) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll;
        synchronized (random) {
            roll = random.nextInt(total);
        }
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }
}
