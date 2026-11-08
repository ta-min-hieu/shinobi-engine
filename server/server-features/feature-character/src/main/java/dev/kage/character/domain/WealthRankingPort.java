package dev.kage.character.domain;

import java.util.Map;

/** Xếp hạng giàu có (ADR-241). */
public interface WealthRankingPort {

    /** Tối đa {@code limit} nhân vật nhiều linh thạch nhất (có ít nhất 1), theo thứ tự giảm dần. */
    Map<Long, Long> richest(int limit);
}
