package dev.kage.character.domain;

import java.time.Instant;
import java.util.List;

/** Tân thủ trong ngày (ADR-417) — đọc {@code character.created_at}. */
public interface NewPlayersPort {

    /** Số nhân vật tạo từ {@code since} trở đi. */
    int countSince(Instant since);

    /** Tên tối đa {@code limit} nhân vật mới nhất tạo từ {@code since}, mới trước. */
    List<String> newestSince(Instant since, int limit);
}
