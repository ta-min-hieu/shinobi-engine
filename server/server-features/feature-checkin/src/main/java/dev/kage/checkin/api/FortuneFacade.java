package dev.kage.checkin.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

import dev.kage.checkin.domain.FortunePort;
import dev.kage.checkin.domain.FortuneRules;

/**
 * Quẻ bói hằng ngày (ADR-149). Gieo chạm DB — chỉ gọi trên Virtual Thread; {@code characterId} từ session. Phần trăm
 * EXP của quẻ được đọc ở MỖI lượt thưởng hạ quái nên giữ một bản nhớ theo ngày: lượt đầu trong ngày hỏi DB một lần,
 * sau đó đọc RAM; gieo xong thì ghi đè ngay.
 */
public final class FortuneFacade {

    /** Composition không có quẻ bói (test) — không bao giờ gieo, không cộng EXP. */
    public static final FortuneFacade DISABLED = new FortuneFacade(new FortunePort() {
        @Override
        public Draw draw(long characterId, LocalDate day, Instant now, ToIntFunction<int[]> pick) {
            return new Draw(Outcome.ALREADY_DRAWN, Optional.empty());
        }

        @Override
        public Optional<Fortune> today(long characterId, LocalDate day) {
            return Optional.empty();
        }
    });

    /** Phần trăm EXP của một nhân vật trong một ngày. */
    private record Today(LocalDate day, int expPercent) {}

    private final FortunePort port;
    private final SplittableRandom random = new SplittableRandom();
    private final Map<Long, Today> cache = new ConcurrentHashMap<>();

    /** Dựng ở composition root. */
    public FortuneFacade(FortunePort port) {
        this.port = port;
    }

    /** Gieo quẻ hôm nay. */
    public FortunePort.Draw draw(long characterId, Instant now) {
        LocalDate day = FortuneRules.day(now);
        FortunePort.Draw draw = port.draw(characterId, day, now, this::pick);
        draw.fortune().ifPresent(fortune -> cache.put(characterId, new Today(day, fortune.expPercent())));
        return draw;
    }

    /** {@code amount} EXP đánh quái sau quẻ hôm nay của nhân vật (chưa gieo ⇒ nguyên). */
    public long boostedExp(long characterId, long amount, Instant now) {
        return FortuneRules.boosted(amount, expPercent(characterId, now));
    }

    private int expPercent(long characterId, Instant now) {
        LocalDate day = FortuneRules.day(now);
        Today cached = cache.get(characterId);
        if (cached != null && cached.day().equals(day)) {
            return cached.expPercent();
        }
        int percent = port.today(characterId, day)
                .map(FortunePort.Fortune::expPercent)
                .orElse(0);
        cache.put(characterId, new Today(day, percent));
        return percent;
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
