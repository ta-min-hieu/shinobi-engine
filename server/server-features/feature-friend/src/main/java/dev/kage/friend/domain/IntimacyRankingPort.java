package dev.kage.friend.domain;

import java.util.List;

/** Xếp hạng thân mật (ADR-434) — cột {@code friendship.intimacy} (V80) của các cặp đã là bạn. */
public interface IntimacyRankingPort {

    /** Một cặp bạn và điểm thân mật. */
    record Pair(String first, String second, int intimacy) {}

    /** Top {@code limit} cặp có thân mật cao nhất (chỉ cặp > 0). */
    List<Pair> top(int limit);
}
