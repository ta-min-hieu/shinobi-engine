package dev.kage.monster.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** ADR-081 D4 — hạng theo sát thương giảm dần, hoà thì id nhỏ trước; bậc thưởng đầu tiên phủ hạng. */
class WorldBossRankingTest {

    @Test
    void shouldRankByDamageThenId_skippingZero() {
        List<WorldBossRanking.Entry> ranked = WorldBossRanking.rank(Map.of(5L, 100L, 3L, 100L, 9L, 300L, 7L, 0L));

        assertThat(ranked)
                .containsExactly(
                        new WorldBossRanking.Entry(1, 9L, 300L),
                        new WorldBossRanking.Entry(2, 3L, 100L),
                        new WorldBossRanking.Entry(3, 5L, 100L));
    }

    @Test
    void shouldPickTheFirstTierCoveringTheRank() {
        List<WorldBossReward> tiers = List.of(
                new WorldBossReward(1, 1, 5000L, 77L, 1),
                new WorldBossReward(2, 10, 2000L, 0L, 0),
                new WorldBossReward(1, 1_000_000, 300L, 0L, 0));

        assertThat(WorldBossRanking.rewardFor(1, tiers))
                .map(WorldBossReward::gold)
                .contains(5000L);
        assertThat(WorldBossRanking.rewardFor(7, tiers))
                .map(WorldBossReward::gold)
                .contains(2000L);
        assertThat(WorldBossRanking.rewardFor(42, tiers))
                .map(WorldBossReward::gold)
                .contains(300L);
        assertThat(WorldBossRanking.rewardFor(0, tiers)).isEmpty();
    }
}
