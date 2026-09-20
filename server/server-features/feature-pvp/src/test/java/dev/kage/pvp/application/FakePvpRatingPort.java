package dev.kage.pvp.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.kage.pvp.domain.EloRating;
import dev.kage.pvp.domain.PvpRating;
import dev.kage.pvp.domain.PvpRatingPort;

/**
 * Fake in-memory THẬT (TESTING.md §4), không Mockito — mô phỏng UPSERT của adapter Postgres thật.
 *
 * <p>{@code synchronized} trên {@link #recordDuelResult} để đọc-tính-ghi ở fake này cũng nguyên tử
 * như {@code PostgresPvpRatingAdapter} thật (khoá {@code FOR UPDATE}) — test race THẬT (2 luồng)
 * chạy trên chính adapter Postgres qua Testcontainers
 * ({@code PostgresPvpRatingAdapterConcurrencyIntegrationTest}, server-database), không phải ở đây:
 * fake này chỉ phục vụ test đơn luồng của {@code RecordDuelResultUseCaseTest}.
 */
final class FakePvpRatingPort implements PvpRatingPort {

    private final Map<Long, PvpRating> ratingsByCharacterId = new HashMap<>();

    @Override
    public Optional<PvpRating> findByCharacterId(long characterId) {
        return Optional.ofNullable(ratingsByCharacterId.get(characterId));
    }

    @Override
    public synchronized EloRating.Result recordDuelResult(
            long winnerCharacterId, long loserCharacterId, EloComputer computer) {
        int winnerRating = currentRating(winnerCharacterId);
        int loserRating = currentRating(loserCharacterId);
        EloRating.Result result = computer.compute(winnerRating, loserRating);
        upsert(winnerCharacterId, result.newWinnerRating(), 1, 0);
        upsert(loserCharacterId, result.newLoserRating(), 0, 1);
        return result;
    }

    private int currentRating(long characterId) {
        return findByCharacterId(characterId).map(PvpRating::rating).orElse(EloRating.DEFAULT_RATING);
    }

    private void upsert(long characterId, int newRating, int winsDelta, int lossesDelta) {
        PvpRating existing = ratingsByCharacterId.get(characterId);
        int wins = (existing == null ? 0 : existing.wins()) + winsDelta;
        int losses = (existing == null ? 0 : existing.losses()) + lossesDelta;
        ratingsByCharacterId.put(characterId, new PvpRating(characterId, newRating, wins, losses, Instant.now()));
    }
}
