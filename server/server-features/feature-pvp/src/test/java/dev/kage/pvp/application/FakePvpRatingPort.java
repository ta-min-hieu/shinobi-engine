package dev.kage.pvp.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.kage.pvp.domain.PvpRating;
import dev.kage.pvp.domain.PvpRatingPort;

/** Fake in-memory THẬT (TESTING.md §4), không Mockito — mô phỏng UPSERT của adapter Postgres thật. */
final class FakePvpRatingPort implements PvpRatingPort {

    private final Map<Long, PvpRating> ratingsByCharacterId = new HashMap<>();

    @Override
    public Optional<PvpRating> findByCharacterId(long characterId) {
        return Optional.ofNullable(ratingsByCharacterId.get(characterId));
    }

    @Override
    public void recordMatchResult(
            long winnerCharacterId, int winnerNewRating, long loserCharacterId, int loserNewRating) {
        upsert(winnerCharacterId, winnerNewRating, 1, 0);
        upsert(loserCharacterId, loserNewRating, 0, 1);
    }

    private void upsert(long characterId, int newRating, int winsDelta, int lossesDelta) {
        PvpRating existing = ratingsByCharacterId.get(characterId);
        int wins = (existing == null ? 0 : existing.wins()) + winsDelta;
        int losses = (existing == null ? 0 : existing.losses()) + lossesDelta;
        ratingsByCharacterId.put(characterId, new PvpRating(characterId, newRating, wins, losses, Instant.now()));
    }
}
