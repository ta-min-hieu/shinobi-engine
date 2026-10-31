package dev.kage.world.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.random.RandomGenerator;

/**
 * Luật kỳ ngộ (ADR-121) — hàm thuần. Mỗi lần hạ quái ở world chính có {@value #TRIGGER_PERCENT}% gặp kỳ ngộ, tối đa
 * {@value #DAILY_LIMIT} lần một ngày (ngày theo giờ Việt Nam), mỗi lúc chỉ một kỳ ngộ đang chờ; kỳ ngộ không chọn trong
 * {@link #EXPIRY} thì tan.
 */
public final class EncounterRules {

    public static final int TRIGGER_PERCENT = 2;
    public static final int DAILY_LIMIT = 3;
    public static final Duration EXPIRY = Duration.ofMinutes(10);

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int PERCENT = 100;

    private EncounterRules() {}

    /** Lần hạ quái này có gặp kỳ ngộ không (chưa xét trần ngày — trần ngày kiểm trong giao dịch). */
    public static boolean triggers(RandomGenerator random) {
        return random.nextInt(PERCENT) < TRIGGER_PERCENT;
    }

    /** Đầu ngày (giờ Việt Nam) chứa {@code now} — mốc đếm trần ngày. */
    public static Instant dayStart(Instant now) {
        LocalDate day = now.atZone(VIETNAM).toLocalDate();
        return day.atStartOfDay(VIETNAM).toInstant();
    }

    /** Chọn một kỳ ngộ theo trọng số; {@code weights} không rỗng, mọi phần tử {@code > 0}. */
    public static int pickWeighted(int[] weights, RandomGenerator random) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }
}
