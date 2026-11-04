package dev.kage.character.domain;

import java.time.Instant;
import java.util.List;

/** Sổ vàng cá nhân (ADR-169) — đọc các lần vàng đổi gần nhất của một nhân vật (mới nhất trước). */
public interface GoldHistoryPort {

    /** Một lần đổi: số đổi (âm = trừ), số dư sau đó, khoá lý do, lúc đổi. */
    record Entry(long delta, long balance, String reasonKey, Instant at) {}

    List<Entry> recent(long characterId, int limit);
}
