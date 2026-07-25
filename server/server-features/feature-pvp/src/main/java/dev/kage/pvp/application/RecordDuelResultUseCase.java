package dev.kage.pvp.application;

import dev.kage.pvp.domain.EloRating;
import dev.kage.pvp.domain.PvpRating;
import dev.kage.pvp.domain.PvpRatingPort;

/** Chốt kết quả 1 trận Arena — tính Elo mới rồi ghi nguyên tử qua {@link PvpRatingPort}. */
public final class RecordDuelResultUseCase {

    private final PvpRatingPort port;

    public RecordDuelResultUseCase(PvpRatingPort port) {
        this.port = port;
    }

    /** @return rating MỚI của cả 2 sau trận — gateway dùng để đóng gói {@code DuelResultEvent}. */
    public DuelSettlement execute(long winnerCharacterId, long loserCharacterId) {
        int winnerRating = currentRating(winnerCharacterId);
        int loserRating = currentRating(loserCharacterId);
        EloRating.Result result = EloRating.computeNewRatings(winnerRating, loserRating);
        port.recordMatchResult(
                winnerCharacterId, result.newWinnerRating(), loserCharacterId, result.newLoserRating());
        return new DuelSettlement(
                winnerCharacterId, result.newWinnerRating(), loserCharacterId, result.newLoserRating());
    }

    private int currentRating(long characterId) {
        return port.findByCharacterId(characterId).map(PvpRating::rating).orElse(EloRating.DEFAULT_RATING);
    }

    /** Gộp tham số kết quả trả về (STATIC-ANALYSIS.md). */
    public record DuelSettlement(
            long winnerCharacterId, int winnerNewRating, long loserCharacterId, int loserNewRating) {
    }
}
