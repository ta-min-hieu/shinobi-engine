package dev.kage.checkin.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Luật chu kỳ điểm danh (ADR-060) — hàm thuần.
 *
 * <p>Ngày trôi theo mốc 0h giờ Việt Nam — CÙNG mốc nhiệm vụ ngày ({@code DailyReset} của feature-quest,
 * ADR-055). Hằng số được lặp lại ở đây thay vì import: feature không phụ thuộc feature (Điều 23); đổi
 * một bên thì đổi cả hai (ADR-060 ghi rõ).
 */
public final class CheckInCycle {

    /** Số ngày một chu kỳ. */
    public static final int DAYS = 7;

    /** Múi giờ của mốc qua ngày. */
    public static final ZoneId RESET_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private CheckInCycle() {}

    /** Hôm nay theo mốc của trò chơi. */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(RESET_ZONE));
    }

    /** Hôm nay đã điểm danh chưa. */
    public static boolean claimedOn(CheckInState state, LocalDate today) {
        return !state.isNever() && state.lastClaimDate().equals(today);
    }

    /**
     * Ngày kế tiếp trong chu kỳ — bỏ một ngày lịch KHÔNG đặt lại về 1 (Q2 theo khuyến nghị: không phạt
     * người bận); hết ngày 7 thì quay lại ngày 1.
     */
    public static int nextDay(CheckInState state) {
        return state.lastDay() % DAYS + 1;
    }
}
