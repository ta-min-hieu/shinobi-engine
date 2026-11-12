package dev.kage.pvp.domain;

import java.time.Instant;
import java.util.List;

/** Lịch sử đấu tay đôi (ADR-280) — đọc audit {@code DUEL_RESULT} ghi cùng giao dịch tính Elo. */
public interface DuelHistoryPort {

    /** Một trận nhìn từ phía người hỏi. */
    record Duel(String opponentName, boolean won, int ratingAfter, Instant at) {}

    /** Tối đa {@code limit} trận gần nhất, mới trước. */
    List<Duel> recent(long characterId, int limit);
}
