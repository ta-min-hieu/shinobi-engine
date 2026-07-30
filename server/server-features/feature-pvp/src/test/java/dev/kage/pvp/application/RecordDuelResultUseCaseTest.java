package dev.kage.pvp.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.pvp.domain.EloRating;

class RecordDuelResultUseCaseTest {

    private final FakePvpRatingPort port = new FakePvpRatingPort();
    private final RecordDuelResultUseCase useCase = new RecordDuelResultUseCase(port);

    @Test
    void shouldStartBothAtDefaultRating_whenNeitherHasPlayedBefore() {
        RecordDuelResultUseCase.DuelSettlement settlement = useCase.execute(1L, 2L);

        // Cùng rating khởi điểm — thắng nhận +16, thua mất 16 (K=32, expected=0.5 cả 2 bên).
        assertThat(settlement.winnerNewRating()).isEqualTo(EloRating.DEFAULT_RATING + 16);
        assertThat(settlement.loserNewRating()).isEqualTo(EloRating.DEFAULT_RATING - 16);
    }

    @Test
    void shouldPersistWinsAndLosses_afterMultipleMatches() {
        useCase.execute(1L, 2L);
        useCase.execute(1L, 2L);

        assertThat(port.findByCharacterId(1L).orElseThrow().wins()).isEqualTo(2);
        assertThat(port.findByCharacterId(1L).orElseThrow().losses()).isEqualTo(0);
        assertThat(port.findByCharacterId(2L).orElseThrow().wins()).isEqualTo(0);
        assertThat(port.findByCharacterId(2L).orElseThrow().losses()).isEqualTo(2);
    }

    @Test
    void shouldGainFewerPoints_whenHigherRatedPlayerBeatsLowerRatedPlayer() {
        // Thắng "hạt giống" (rating cao đấu rating thấp) phải nhận ÍT điểm hơn (Elo cốt lõi) —
        // dựng sẵn 1 (cao) và 2 (thấp) qua vài trận trước rồi so sánh mức tăng.
        useCase.execute(1L, 2L); // 1 thắng vài lần trước để tách rating.
        useCase.execute(1L, 2L);
        useCase.execute(1L, 2L);
        int ratingBefore = port.findByCharacterId(1L).orElseThrow().rating();

        RecordDuelResultUseCase.DuelSettlement settlement = useCase.execute(1L, 2L);

        assertThat(settlement.winnerNewRating() - ratingBefore).isLessThan(16);
    }
}
