package dev.kage.checkin.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** Thưởng thời gian online (ADR-096) — mốc và cách đếm giây theo ngày, hàm thuần. Số là PLACEHOLDER cân bằng. */
public final class OnlineRewardPolicy {

    /** Ngày tính theo giờ Việt Nam — cùng mốc với điểm danh (ADR-060). */
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final int SECONDS_PER_MINUTE = 60;

    /**
     * Một mốc.
     *
     * @param bit vị trí bit trong {@code online_time.claimed}
     * @param itemCode mã vật phẩm thưởng; rỗng = không có
     */
    public record Milestone(int minutes, int bit, long gold, String itemCode, int itemQuantity) {

        public int requiredSeconds() {
            return minutes * SECONDS_PER_MINUTE;
        }

        public int mask() {
            return 1 << bit;
        }
    }

    /** Ba mốc mỗi ngày, tăng dần. */
    public static final List<Milestone> MILESTONES = List.of(
            new Milestone(10, 0, 300L, "", 0),
            new Milestone(30, 1, 0L, "minor_healing_pill", 2),
            new Milestone(60, 2, 1_000L, "spirit_herb", 1));

    private OnlineRewardPolicy() {}

    public static Optional<Milestone> milestone(int minutes) {
        return MILESTONES.stream().filter(m -> m.minutes() == minutes).findFirst();
    }

    /** Ngày (giờ Việt Nam) của {@code instant}. */
    public static LocalDate dayOf(Instant instant) {
        return LocalDate.ofInstant(instant, VIETNAM);
    }

    /**
     * Số giây của phiên {@code [enteredAt, until)} thuộc về NGÀY của {@code until} — phiên vắt qua nửa đêm chỉ tính
     * phần sau nửa đêm.
     */
    public static int secondsOnDayOf(Instant enteredAt, Instant until) {
        Instant startOfDay = dayOf(until).atStartOfDay(VIETNAM).toInstant();
        Instant from = enteredAt.isAfter(startOfDay) ? enteredAt : startOfDay;
        long seconds = Duration.between(from, until).toSeconds();
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, seconds));
    }
}
