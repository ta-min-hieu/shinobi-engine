package dev.kage.monster.domain;

import java.util.List;

/** Xếp hạng hạ boss (ADR-442) — đếm audit {@code BOSS_KILL} (ADR-296). */
public interface BossKillRankingPort {

    /** Một dòng bảng: tên nhân vật và điểm. */
    record Ranked(String name, long value) {}

    /** Số lần hạ boss, nhiều trước. */
    List<Ranked> top(int limit);
}
