package dev.kage.party.domain;

import java.time.Instant;
import java.util.List;

/** Người vừa chơi cùng (ADR-329) — bảng {@code recent_player}. */
public interface RecentPlayerPort {

    /** Một người từng chung nhóm và lần gần nhất. */
    record Entry(long otherId, Instant metAt) {}

    /**
     * Ghi (hoặc làm mới) {@code otherId} vào danh sách của {@code characterId}, chỉ giữ {@code keep} người mới nhất.
     */
    void record(long characterId, long otherId, Instant at, int keep);

    /** Tối đa {@code limit} người, mới trước. */
    List<Entry> recent(long characterId, int limit);
}
