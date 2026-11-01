package dev.kage.world.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** ADR-144 — thời tiết đổi tất định theo đồng hồ: có trong {@code active} phút đầu của mỗi chu kỳ, lệch theo offset. */
class WeatherRegionTest {

    /** Đầu một giờ tròn — phút epoch chia hết cho 60. */
    private static final Instant HOUR = Instant.parse("2026-11-01T10:00:00Z");

    private static final WeatherRegion RAIN = new WeatherRegion("rain", -40f, 100f, "SPIRIT_RAIN", 60, 20, 0);

    @Test
    void shouldBeActiveOnlyDuringTheFirstActiveMinutesOfEachPeriod() {
        assertThat(RAIN.isActive(HOUR)).isTrue();
        assertThat(RAIN.isActive(HOUR.plusSeconds(19 * 60 + 59))).isTrue();
        assertThat(RAIN.isActive(HOUR.plusSeconds(20 * 60))).isFalse();
        assertThat(RAIN.isActive(HOUR.plusSeconds(59 * 60))).isFalse();
        assertThat(RAIN.isActive(HOUR.plusSeconds(60 * 60))).isTrue();
    }

    @Test
    void shouldShiftTheWindowByTheOffset() {
        WeatherRegion storm = new WeatherRegion("storm", 307f, 417f, "SANDSTORM", 60, 15, 20);

        assertThat(storm.isActive(HOUR)).isFalse();
        assertThat(storm.isActive(HOUR.plusSeconds(40 * 60))).isTrue();
        assertThat(storm.isActive(HOUR.plusSeconds(55 * 60))).isFalse();
    }
}
