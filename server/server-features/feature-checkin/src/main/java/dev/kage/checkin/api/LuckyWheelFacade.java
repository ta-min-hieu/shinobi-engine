package dev.kage.checkin.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.ToIntFunction;

import dev.kage.checkin.domain.FortuneRules;
import dev.kage.checkin.domain.LuckyWheelPort;

/** Vòng quay may mắn (ADR-151). Chạm DB — chỉ gọi trên Virtual Thread; {@code characterId} từ session. */
public final class LuckyWheelFacade {

    /** Composition không có vòng quay (test) — không ô nào, không lượt nào. */
    public static final LuckyWheelFacade DISABLED = new LuckyWheelFacade(new LuckyWheelPort() {
        @Override
        public Wheel wheel(long characterId, LocalDate day) {
            return new Wheel(List.of(), new Spins(0, 0));
        }

        @Override
        public Spin spin(long characterId, LocalDate day, Instant now, ToIntFunction<int[]> pick) {
            return new Spin(Outcome.NO_SPINS_LEFT, Optional.empty(), false, new Spins(0, 0));
        }
    });

    private final LuckyWheelPort port;
    private final SplittableRandom random = new SplittableRandom();

    /** Dựng ở composition root. */
    public LuckyWheelFacade(LuckyWheelPort port) {
        this.port = port;
    }

    /** Vòng quay hôm nay (giờ Việt Nam). */
    public LuckyWheelPort.Wheel wheel(long characterId, Instant now) {
        return port.wheel(characterId, FortuneRules.day(now));
    }

    /** Quay một lượt — server chọn ô theo trọng số. */
    public LuckyWheelPort.Spin spin(long characterId, Instant now) {
        return port.spin(characterId, FortuneRules.day(now), now, this::pick);
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
