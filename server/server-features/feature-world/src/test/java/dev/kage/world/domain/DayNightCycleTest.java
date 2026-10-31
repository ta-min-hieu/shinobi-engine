package dev.kage.world.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/** ADR-123 — một giờ thật một ngày, phút 40–59 là đêm. */
class DayNightCycleTest {

    @Test
    void lastTwentyMinutesOfEveryHourShouldBeNight() {
        assertThat(DayNightCycle.isNight(Instant.parse("2026-10-31T06:00:00Z"))).isFalse();
        assertThat(DayNightCycle.isNight(Instant.parse("2026-10-31T06:39:59Z"))).isFalse();
        assertThat(DayNightCycle.isNight(Instant.parse("2026-10-31T06:40:00Z"))).isTrue();
        assertThat(DayNightCycle.isNight(Instant.parse("2026-10-31T06:59:59Z"))).isTrue();
        assertThat(DayNightCycle.isNight(Instant.parse("2026-10-31T07:00:00Z"))).isFalse();
    }

    @Test
    void shouldCountDownToTheNextChange() {
        assertThat(DayNightCycle.secondsUntilChange(Instant.parse("2026-10-31T06:39:00Z")))
                .isEqualTo(60L);
        assertThat(DayNightCycle.secondsUntilChange(Instant.parse("2026-10-31T06:59:30Z")))
                .isEqualTo(30L);
    }

    @Test
    void nightShouldHitHarderAndDropAboutTwentyPercentMore() {
        Instant night = Instant.parse("2026-10-31T06:45:00Z");
        Instant day = Instant.parse("2026-10-31T06:15:00Z");
        assertThat(DayNightCycle.monsterDamagePercent(night)).isEqualTo(115);
        assertThat(DayNightCycle.monsterDamagePercent(day)).isEqualTo(100);

        SplittableRandom random = new SplittableRandom(3);
        int rolls = 0;
        int kills = 100_000;
        for (int i = 0; i < kills; i++) {
            rolls += DayNightCycle.lootRolls(night, random);
            assertThat(DayNightCycle.lootRolls(day, random)).isEqualTo(1);
        }
        assertThat(rolls).isBetween(119_000, 121_000);
    }
}
