package dev.kage.pvp.domain;

import java.util.Optional;

/** Port — DB-agnostic (Điều 23). Implementation thật ({@code PostgresPvpRatingAdapter}) ở server-database. */
public interface PvpRatingPort {

    Optional<PvpRating> findByCharacterId(long characterId);

    /**
     * Ghi rating mới cho CẢ 2 người sau 1 trận, nguyên tử (UPSERT — có thể là trận đầu tiên của 1
     * hoặc cả 2, chưa có dòng {@code pvp_rating} nào). {@code wins}/{@code losses} cộng dồn +1 ở
     * đúng phía, {@code rating} ghi đè bằng giá trị MỚI đã tính (không cộng dồn).
     */
    void recordMatchResult(long winnerCharacterId, int winnerNewRating, long loserCharacterId, int loserNewRating);
}
