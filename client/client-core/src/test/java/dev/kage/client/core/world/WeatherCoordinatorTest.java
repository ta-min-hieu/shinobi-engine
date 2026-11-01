package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.WeatherEvent;
import dev.kage.protocol.WeatherKind;
import dev.kage.protocol.WeatherZone;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.WorldKind;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/** ADR-144 — thời tiết theo vị trí của chính mình; đậm/nhạt dần; ngoài world chính thì trời quang. */
class WeatherCoordinatorTest {

    private final WeatherCoordinator weather = new WeatherCoordinator();

    @BeforeEach
    void setUp() throws InvalidProtocolBufferException {
        weather.dispatch(frame(
                Opcode.OPCODE_WEATHER_EVENT_VALUE,
                WeatherEvent.newBuilder()
                        .addZones(zone(-40f, 100f, WeatherKind.WEATHER_KIND_SPIRIT_RAIN))
                        .addZones(zone(307f, 417f, WeatherKind.WEATHER_KIND_SANDSTORM))
                        .addZones(zone(500f, 450f, WeatherKind.WEATHER_KIND_SANDSTORM))
                        .build()));
    }

    @Test
    void shouldPickTheWeatherWhereIStand() {
        assertThat(weather.kindAt(0f)).isEqualTo(WeatherKind.WEATHER_KIND_SPIRIT_RAIN);
        assertThat(weather.kindAt(350f)).isEqualTo(WeatherKind.WEATHER_KIND_SANDSTORM);
        assertThat(weather.kindAt(200f)).isEqualTo(WeatherKind.WEATHER_KIND_UNSPECIFIED);
        assertThat(weather.kindAt(470f)).as("vùng ngược bị bỏ").isEqualTo(WeatherKind.WEATHER_KIND_UNSPECIFIED);
    }

    @Test
    void shouldClearTheSkyOutsideTheMainWorld() throws InvalidProtocolBufferException {
        weather.dispatch(frame(
                Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE,
                WorldChangedEvent.newBuilder()
                        .setWorldKind(WorldKind.WORLD_KIND_DUNGEON)
                        .build()));

        assertThat(weather.kindAt(0f)).isEqualTo(WeatherKind.WEATHER_KIND_UNSPECIFIED);
    }

    @Test
    void shouldFadeOutTheOldWeatherBeforeFadingInTheNew() {
        WeatherOverlay overlay = weather.overlay();
        overlay.setTarget(WeatherKind.WEATHER_KIND_SPIRIT_RAIN);
        for (int i = 0; i < 10; i++) {
            overlay.update(0.5f);
        }
        assertThat(overlay.shown()).isEqualTo(WeatherKind.WEATHER_KIND_SPIRIT_RAIN);
        assertThat(overlay.strength()).isEqualTo(1f);

        overlay.setTarget(WeatherKind.WEATHER_KIND_SANDSTORM);
        overlay.update(1f);
        assertThat(overlay.shown()).isEqualTo(WeatherKind.WEATHER_KIND_SPIRIT_RAIN);
        assertThat(overlay.strength()).isEqualTo(0.5f);

        overlay.update(1f);
        overlay.update(1f);
        assertThat(overlay.shown()).isEqualTo(WeatherKind.WEATHER_KIND_SANDSTORM);
    }

    private static WeatherZone zone(float min, float max, WeatherKind kind) {
        return WeatherZone.newBuilder()
                .setCode("z")
                .setMinX(min)
                .setMaxX(max)
                .setKind(kind)
                .build();
    }

    private static PacketCodec.Frame frame(int opcode, MessageLite message) {
        return new PacketCodec.Frame(new PacketHeader(opcode, ProtocolVersion.CURRENT, 0), message.toByteArray());
    }
}
