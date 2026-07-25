package dev.kage.pvp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EloRatingTest {

    @Test
    void shouldSplitPointsEvenly_whenBothPlayersHaveEqualRating() {
        EloRating.Result result = EloRating.computeNewRatings(1000, 1000);

        assertThat(result.newWinnerRating()).isEqualTo(1016);
        assertThat(result.newLoserRating()).isEqualTo(984);
    }

    @Test
    void shouldGainMorePoints_whenUnderdogBeatsHigherRatedOpponent() {
        EloRating.Result underdogWins = EloRating.computeNewRatings(1000, 1200);
        EloRating.Result favoriteWins = EloRating.computeNewRatings(1200, 1000);

        int underdogGain = underdogWins.newWinnerRating() - 1000;
        int favoriteGain = favoriteWins.newWinnerRating() - 1200;
        assertThat(underdogGain).isGreaterThan(favoriteGain);
    }
}
