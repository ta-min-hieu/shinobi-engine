package dev.kage.character.domain;

import java.time.Instant;
import java.util.List;

/** Lịch sử tử vong (ADR-337) — audit {@code DEATH}. */
public interface DeathLogPort {

    /** Một lần bị hạ: người chơi ({@code killerName}) HOẶC quái ({@code monsterCode}); cả hai rỗng = không rõ. */
    record Entry(String killerName, String monsterCode, int x, Instant at) {}

    /** Ghi một lần bị hạ. */
    void record(long characterId, Entry entry);

    /** Tối đa {@code limit} lần mới nhất, mới trước. */
    List<Entry> recent(long characterId, int limit);
}
