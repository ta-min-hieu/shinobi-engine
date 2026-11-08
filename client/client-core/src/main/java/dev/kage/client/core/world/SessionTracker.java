package dev.kage.client.core.world;

import java.util.OptionalLong;

/**
 * Bộ đếm của MỘT phiên chơi (ADR-271/272) — thuần dữ liệu, không libGDX: EXP, linh thạch, quái hạ, vật phẩm nhặt từ lúc
 * vào world, cùng mốc EXP đầu phiên để ước tính EXP/giờ. Chỉ đếm những gì SERVER đã báo (không tự suy gameplay).
 */
final class SessionTracker {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    /** Dưới thời lượng này chưa đủ để ước tính tốc độ. */
    static final long MIN_RATE_MILLIS = 60_000L;

    private final long startedAtMillis;
    private long firstTotalExp = -1L;
    private long lastTotalExp = -1L;
    private long expIntoLevel;
    private long expForNextLevel;
    private int levelsGained;
    private long goldGained;
    private long goldSpent;
    private int kills;
    private int itemsLooted;

    SessionTracker(long startedAtMillis) {
        this.startedAtMillis = startedAtMillis;
    }

    /** {@code ExperienceChangedEvent}: lần đầu chỉ ghi mốc (gói vào world), các lần sau cộng phần tăng. */
    void onExperience(long totalExp, long intoLevel, long forNextLevel, boolean leveledUp) {
        if (firstTotalExp < 0L) {
            firstTotalExp = totalExp;
        }
        lastTotalExp = Math.max(lastTotalExp, totalExp);
        expIntoLevel = intoLevel;
        expForNextLevel = forNextLevel;
        if (leveledUp) {
            levelsGained++;
        }
    }

    /** {@code GoldChangedEvent.delta} — dương là thu, âm là chi. */
    void onGold(long delta) {
        if (delta > 0) {
            goldGained += delta;
        } else {
            goldSpent -= delta;
        }
    }

    void onKill() {
        kills++;
    }

    void onItemGranted(int quantity) {
        itemsLooted += Math.max(0, quantity);
    }

    long expGained() {
        return firstTotalExp < 0L ? 0L : lastTotalExp - firstTotalExp;
    }

    long goldGained() {
        return goldGained;
    }

    long goldSpent() {
        return goldSpent;
    }

    int kills() {
        return kills;
    }

    int itemsLooted() {
        return itemsLooted;
    }

    int levelsGained() {
        return levelsGained;
    }

    long elapsedMillis(long nowMillis) {
        return Math.max(0L, nowMillis - startedAtMillis);
    }

    /** EXP/giờ từ đầu phiên; rỗng khi phiên còn quá ngắn. */
    OptionalLong expPerHour(long nowMillis) {
        long elapsed = elapsedMillis(nowMillis);
        if (elapsed < MIN_RATE_MILLIS) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(expGained() * MILLIS_PER_HOUR / elapsed);
    }

    /** Số phút ước tính để lên cấp kế với tốc độ hiện tại; rỗng khi chưa có tốc độ / đã cấp tối đa. */
    OptionalLong minutesToNextLevel(long nowMillis) {
        OptionalLong rate = expPerHour(nowMillis);
        long remaining = expForNextLevel - expIntoLevel;
        if (rate.isEmpty() || rate.getAsLong() <= 0L || expForNextLevel <= 0L || remaining <= 0L) {
            return OptionalLong.empty();
        }
        return OptionalLong.of((remaining * 60L + rate.getAsLong() - 1L) / rate.getAsLong());
    }
}
