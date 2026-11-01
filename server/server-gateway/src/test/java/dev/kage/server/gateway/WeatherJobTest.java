package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.WeatherEvent;
import dev.kage.protocol.WeatherKind;
import dev.kage.world.domain.WeatherRegion;

/** ADR-144 — gói 340 chỉ chứa vùng ĐANG có thời tiết, đúng loại. */
class WeatherJobTest {

    private static final Instant HOUR = Instant.parse("2026-11-01T10:00:00Z");

    private static final List<WeatherRegion> REGIONS = List.of(
            new WeatherRegion("sect_spirit_rain", -40f, 100f, "SPIRIT_RAIN", 60, 20, 0),
            new WeatherRegion("wasteland_sandstorm", 307f, 417f, "SANDSTORM", 60, 15, 20));

    @Test
    void shouldListOnlyTheActiveRegions() {
        WeatherEvent event = WeatherJob.event(REGIONS, HOUR);

        assertThat(event.getZonesList()).hasSize(1);
        assertThat(event.getZones(0).getCode()).isEqualTo("sect_spirit_rain");
        assertThat(event.getZones(0).getKind()).isEqualTo(WeatherKind.WEATHER_KIND_SPIRIT_RAIN);
        assertThat(event.getZones(0).getMinX()).isEqualTo(-40f);
    }

    @Test
    void shouldSendAClearSkyWhenNothingIsActive() {
        assertThat(WeatherJob.event(REGIONS, HOUR.plusSeconds(21 * 60)).getZonesList())
                .isEmpty();
    }
}
