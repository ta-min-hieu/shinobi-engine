package dev.kage.world.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** ADR-129 — tiểu linh mạch: sản lượng theo giờ thật, làm tròn xuống, kho có trần. */
class CaveRulesTest {

    private static final Instant SINCE = Instant.parse("2026-10-31T00:00:00Z");

    @Test
    void shouldProduceThirtyPerHourPerLevelPlusDecorationBonus() {
        assertThat(CaveRules.perHour(1, 0)).isEqualTo(30L);
        assertThat(CaveRules.perHour(5, 0)).isEqualTo(150L);
        assertThat(CaveRules.perHour(2, 40)).isEqualTo(84L);
    }

    @Test
    void shouldAccrueByRealTimeRoundingDown() {
        assertThat(CaveRules.accrued(1, 0, SINCE, SINCE.plus(Duration.ofMinutes(1))))
                .isZero();
        assertThat(CaveRules.accrued(1, 0, SINCE, SINCE.plus(Duration.ofMinutes(2))))
                .isEqualTo(1L);
        assertThat(CaveRules.accrued(1, 0, SINCE, SINCE.plus(Duration.ofHours(3))))
                .isEqualTo(90L);
        assertThat(CaveRules.accrued(1, 0, SINCE, SINCE.minusSeconds(60)))
                .as("đồng hồ lùi")
                .isZero();
    }

    @Test
    void shouldStopAtTwentyFourHoursOfStorage() {
        assertThat(CaveRules.accrued(1, 0, SINCE, SINCE.plus(Duration.ofDays(3))))
                .isEqualTo(CaveRules.capacity(1, 0))
                .isEqualTo(720L);
    }

    @Test
    void shouldPriceUpgradesByCurrentLevel() {
        assertThat(CaveRules.upgradeCost(1)).isEqualTo(2_000L);
        assertThat(CaveRules.upgradeCost(4)).isEqualTo(8_000L);
    }
}
