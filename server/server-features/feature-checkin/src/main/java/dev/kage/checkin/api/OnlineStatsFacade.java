package dev.kage.checkin.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import dev.kage.checkin.domain.OnlineStatsPort;

/** API thống kê online (ADR-431 chăm chỉ hôm nay, ADR-432 online 7 ngày). Chạm DB — Virtual Thread. */
public final class OnlineStatsFacade {

    /** Số dòng bảng chăm chỉ. */
    public static final int TOP = 10;

    /** Số ngày của bảng online cá nhân. */
    public static final int DAYS = 7;

    /** Không có dữ liệu (composition test). */
    public static final OnlineStatsFacade DISABLED = new OnlineStatsFacade(new OnlineStatsPort() {
        @Override
        public List<Ranked> topOfDay(LocalDate day, int limit) {
            return List.of();
        }

        @Override
        public Map<LocalDate, Integer> daysOf(long characterId, LocalDate from, LocalDate to) {
            return Map.of();
        }
    });

    private final OnlineStatsPort port;

    /** Dựng ở composition root. */
    public OnlineStatsFacade(OnlineStatsPort port) {
        this.port = port;
    }

    /** ADR-431 — top {@value #TOP} của ngày. */
    public List<OnlineStatsPort.Ranked> topOfDay(LocalDate day) {
        return port.topOfDay(day, TOP);
    }

    /** ADR-432 — số giây đã lưu {@value #DAYS} ngày kết thúc ở {@code today}. */
    public Map<LocalDate, Integer> lastDays(long characterId, LocalDate today) {
        return port.daysOf(characterId, today.minusDays(DAYS - 1L), today);
    }
}
