package dev.kage.character.domain;

import java.time.Instant;
import java.util.List;

/** Lịch sử đột phá cảnh giới (ADR-322) — audit {@code BREAKTHROUGH}. */
public interface BreakthroughLogPort {

    /** Một lần đột phá. */
    record Entry(int realm, int level, Instant at) {}

    /** Ghi một lần đột phá. */
    void record(long characterId, int realm, int level);

    /** Mọi lần đột phá đã ghi, cũ trước. */
    List<Entry> history(long characterId);
}
