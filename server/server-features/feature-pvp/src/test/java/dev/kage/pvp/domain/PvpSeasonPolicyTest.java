package dev.kage.pvp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** ADR-087 — Elo đầu mùa kéo nửa đường về 1000; bậc thưởng đầu tiên phủ hạng. */
class PvpSeasonPolicyTest {

    @Test
    void softResetShouldPullHalfwayBackToTheBaseline() {
        assertThat(PvpSeasonPolicy.softReset(1200)).isEqualTo(1100);
        assertThat(PvpSeasonPolicy.softReset(900)).isEqualTo(950);
        assertThat(PvpSeasonPolicy.softReset(EloRating.DEFAULT_RATING)).isEqualTo(EloRating.DEFAULT_RATING);
        assertThat(PvpSeasonPolicy.softReset(1001)).isEqualTo(1000);
        assertThat(PvpSeasonPolicy.softReset(999)).isEqualTo(1000);
    }

    @Test
    void shouldPickTheFirstTierCoveringTheRank() {
        List<PvpSeasonPort.Reward> tiers = List.of(
                new PvpSeasonPort.Reward(1, 1, 10_000L, 5L, 1),
                new PvpSeasonPort.Reward(2, 3, 6_000L, 0L, 0),
                new PvpSeasonPort.Reward(11, 1_000_000, 500L, 0L, 0));

        assertThat(PvpSeasonPolicy.rewardFor(1, tiers))
                .map(PvpSeasonPort.Reward::gold)
                .contains(10_000L);
        assertThat(PvpSeasonPolicy.rewardFor(3, tiers))
                .map(PvpSeasonPort.Reward::gold)
                .contains(6_000L);
        assertThat(PvpSeasonPolicy.rewardFor(7, tiers)).isEmpty();
        assertThat(PvpSeasonPolicy.rewardFor(50, tiers))
                .map(PvpSeasonPort.Reward::gold)
                .contains(500L);
    }
}
