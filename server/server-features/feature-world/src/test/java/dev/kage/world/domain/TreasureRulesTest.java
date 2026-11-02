package dev.kage.world.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-147 — tầm đào theo trục X, hai phía điểm chôn như nhau. */
class TreasureRulesTest {

    private static final TreasurePort.Spot SPOT = new TreasurePort.Spot(1L, "treasure.spot.x", 95f, 0f, "stone_ogre");

    @Test
    void shouldDigOnlyWithinReachOnEitherSide() {
        assertThat(TreasureRules.withinReach(95f, SPOT)).isTrue();
        assertThat(TreasureRules.withinReach(92.5f, SPOT)).isTrue();
        assertThat(TreasureRules.withinReach(97.5f, SPOT)).isTrue();
        assertThat(TreasureRules.withinReach(92.4f, SPOT)).isFalse();
        assertThat(TreasureRules.withinReach(97.6f, SPOT)).isFalse();
    }

    @Test
    void distanceShouldIgnoreDirection() {
        assertThat(TreasureRules.distance(80f, SPOT)).isEqualTo(15f);
        assertThat(TreasureRules.distance(110f, SPOT)).isEqualTo(15f);
    }
}
