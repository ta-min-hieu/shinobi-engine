package dev.kage.character.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-126 — trần cấp theo kiếp, số đợt, sẵn sàng, hình phạt. */
class TribulationRulesTest {

    @Test
    void capShouldSitOnTheNinthTierOfEachEarlyRealm() {
        assertThat(TribulationRules.levelCap(0)).isEqualTo(18);
        assertThat(TribulationRules.levelCap(1)).isEqualTo(36);
        assertThat(TribulationRules.levelCap(4)).isEqualTo(90);
        assertThat(TribulationRules.levelCap(5)).isEqualTo(ExperienceCurve.MAX_LEVEL);
    }

    @Test
    void wavesShouldGrowWithEachTribulation() {
        assertThat(TribulationRules.waves(0)).isEqualTo(3);
        assertThat(TribulationRules.waves(4)).isEqualTo(7);
    }

    @Test
    void readyOnlyWhenTheExpAlreadyPassesTheCap() {
        long atCap = TribulationRules.expToReach(18);
        long enoughForNext = TribulationRules.expToReach(19);
        assertThat(TribulationRules.ready(0, atCap)).isFalse();
        assertThat(TribulationRules.ready(0, enoughForNext)).isTrue();
        assertThat(TribulationRules.ready(5, Long.MAX_VALUE / 4)).isFalse();
    }

    @Test
    void cappedProgressShouldStopAtTheCapButKeepTheExp() {
        long exp = TribulationRules.expToReach(25);
        ExperienceProgress capped = ExperienceCurve.progressOf(exp, 18);
        assertThat(capped.level()).isEqualTo(18);
        assertThat(capped.totalExp()).isEqualTo(exp);
        assertThat(ExperienceCurve.progressOf(exp, 100).level()).isEqualTo(25);
    }

    @Test
    void failPenaltyShouldBeTenPercentOfTheNextLevel() {
        assertThat(TribulationRules.failPenalty(18)).isEqualTo(ExperienceCurve.expForNextLevel(18) / 10);
    }
}
