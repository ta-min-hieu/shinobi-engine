package dev.kage.character.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Tra tên cũ (ADR-343) — bảng {@code character_name_history} (ADR-148). */
public interface NameHistoryPort {

    /** Một lần đổi tên: tên trước khi đổi, lúc đổi. */
    record Entry(String oldName, Instant changedAt) {}

    /** Các tên cũ của nhân vật, mới trước. */
    List<Entry> history(long characterId);

    /** Nhân vật gần nhất từng mang {@code oldName} (không phân biệt hoa thường). */
    Optional<Long> characterWithOldName(String oldName);
}
