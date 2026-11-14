package dev.kage.quest.domain;

import java.util.List;

/** Xếp hạng nhiệm vụ (ADR-441) — số dòng {@code character_quest_progress} đã xong. */
public interface QuestRankingPort {

    /** Một dòng bảng: tên nhân vật và điểm. */
    record Ranked(String name, long value) {}

    /** Số nhiệm vụ COMPLETED + CLAIMED, nhiều trước. */
    List<Ranked> top(int limit);
}
