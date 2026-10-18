package dev.kage.quest.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Mốc làm mới nhiệm vụ ngày (ADR-055 D3) — 0h theo giờ Việt Nam, KHÔNG theo múi giờ máy chủ (container
 * chạy UTC). Hàm thuần: người gọi tự đưa {@code now} (từ {@link java.time.Clock}) để test đứng được ở
 * 23:59 và 00:01.
 */
public final class DailyReset {

    /** Múi giờ của mốc ngày — single-realm (ADR-010), người chơi Việt Nam. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private DailyReset() {}

    /** Thời điểm 0h của "hôm nay" (giờ Việt Nam) chứa {@code now}. */
    public static Instant startOfDay(Instant now) {
        return LocalDate.ofInstant(now, ZONE).atStartOfDay(ZONE).toInstant();
    }
}
