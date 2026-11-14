package dev.kage.character.domain;

import java.util.List;

/** Xếp hạng theo số liệu trọn đời (ADR-391/392) — đọc {@code character_lifetime_stats}. */
public interface LifetimeRankingPort {

    /** Một dòng xếp hạng. */
    record Ranked(String name, long value) {}

    /** ADR-391 — top {@code limit} số lần hạ người chơi (chỉ người có ít nhất 1 lần). */
    List<Ranked> topPlayerKills(int limit);

    /** ADR-392 — top {@code limit} tổng giây đã chơi (phiên đã kết thúc). */
    List<Ranked> topPlaytime(int limit);
}
