package dev.kage.pvp.application;

import dev.kage.pvp.domain.EloRating;
import dev.kage.pvp.domain.PvpRatingPort;

/** Tra rating hiện tại — chưa từng đấu trận nào thì trả điểm khởi điểm (chưa có dòng DB nào). */
public final class GetPvpRatingUseCase {

    private final PvpRatingPort port;

    public GetPvpRatingUseCase(PvpRatingPort port) {
        this.port = port;
    }

    public RatingView execute(long characterId) {
        return port.findByCharacterId(characterId)
                .map(r -> new RatingView(r.rating(), r.wins(), r.losses()))
                .orElse(new RatingView(EloRating.DEFAULT_RATING, 0, 0));
    }

    public record RatingView(int rating, int wins, int losses) {
    }
}
