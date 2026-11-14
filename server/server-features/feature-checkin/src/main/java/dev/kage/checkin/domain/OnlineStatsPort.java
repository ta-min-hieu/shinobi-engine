package dev.kage.checkin.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Thống kê thời gian online (ADR-431/432) — đọc {@code online_time} (phiên đã kết thúc). */
public interface OnlineStatsPort {

    /** Một dòng xếp hạng. */
    record Ranked(long characterId, String name, int seconds) {}

    /** ADR-431 — top {@code limit} số giây online của ngày {@code day}. */
    List<Ranked> topOfDay(LocalDate day, int limit);

    /** ADR-432 — số giây đã lưu theo ngày trong khoảng [{@code from}, {@code to}] của một nhân vật. */
    Map<LocalDate, Integer> daysOf(long characterId, LocalDate from, LocalDate to);
}
