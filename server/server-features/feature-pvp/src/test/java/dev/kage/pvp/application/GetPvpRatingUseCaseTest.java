package dev.kage.pvp.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.pvp.domain.EloRating;

class GetPvpRatingUseCaseTest {

    private final FakePvpRatingPort port = new FakePvpRatingPort();
    private final GetPvpRatingUseCase useCase = new GetPvpRatingUseCase(port);

    @Test
    void shouldReturnDefaultRating_whenCharacterNeverPlayed() {
        GetPvpRatingUseCase.RatingView view = useCase.execute(1L);

        assertThat(view.rating()).isEqualTo(EloRating.DEFAULT_RATING);
        assertThat(view.wins()).isZero();
        assertThat(view.losses()).isZero();
    }

    @Test
    void shouldReturnPersistedRating_afterAMatch() {
        new RecordDuelResultUseCase(port).execute(1L, 2L);

        GetPvpRatingUseCase.RatingView winnerView = useCase.execute(1L);
        assertThat(winnerView.rating()).isEqualTo(EloRating.DEFAULT_RATING + 16);
        assertThat(winnerView.wins()).isEqualTo(1);
        assertThat(winnerView.losses()).isZero();
    }
}
