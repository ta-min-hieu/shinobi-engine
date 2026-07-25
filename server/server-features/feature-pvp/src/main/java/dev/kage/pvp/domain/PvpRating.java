package dev.kage.pvp.domain;

import java.time.Instant;
import java.util.Objects;

/** Aggregate — điểm MMR/rating của 1 nhân vật trong Arena 1v1 (GDD §6). */
public record PvpRating(long characterId, int rating, int wins, int losses, Instant updatedAt) {

    public PvpRating {
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (rating < 0) {
            throw new IllegalArgumentException("Rating không được âm: " + rating);
        }
        if (wins < 0 || losses < 0) {
            throw new IllegalArgumentException("Wins/losses không được âm: wins=" + wins + ", losses=" + losses);
        }
    }
}
