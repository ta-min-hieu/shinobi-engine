package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.WeatherEvent;
import dev.kage.protocol.WeatherKind;
import dev.kage.protocol.WeatherZone;
import dev.kage.world.domain.WeatherRegion;

/**
 * Thời tiết theo vùng (ADR-144) — mỗi {@value #PERIOD_SECONDS} giây phát gói 340 cho mọi người online: các vùng ĐANG có
 * thời tiết. Gửi lặp vô điều kiện cùng lý do với {@link DayNightJob}: người vừa vào world nhận trạng thái trong tối đa
 * một nhịp. Vùng nạp MỘT lần lúc khởi động (config tĩnh). Chỉ hiển thị — không đổi gì trong World.
 */
final class WeatherJob {

    private static final long PERIOD_SECONDS = 30L;

    private WeatherJob() {}

    static ExpansionJobs.Job job(List<WeatherRegion> regions, ConnectedCharacterRegistry registry, Clock clock) {
        List<WeatherRegion> loaded = List.copyOf(regions);
        return new ExpansionJobs.Job(
                "thời tiết",
                PERIOD_SECONDS,
                () -> registry.broadcastToAll(
                        Opcode.OPCODE_WEATHER_EVENT_VALUE,
                        event(loaded, clock.instant()).toByteArray()));
    }

    /** Gói của lúc {@code now}: chỉ vùng đang có thời tiết. */
    static WeatherEvent event(List<WeatherRegion> regions, Instant now) {
        WeatherEvent.Builder event = WeatherEvent.newBuilder();
        for (WeatherRegion region : regions) {
            if (region.isActive(now)) {
                event.addZones(WeatherZone.newBuilder()
                        .setCode(region.code())
                        .setMinX(region.minX())
                        .setMaxX(region.maxX())
                        .setKind(kindOf(region.kind())));
            }
        }
        return event.build();
    }

    private static WeatherKind kindOf(String kind) {
        return switch (kind) {
            case "SPIRIT_RAIN" -> WeatherKind.WEATHER_KIND_SPIRIT_RAIN;
            case "SANDSTORM" -> WeatherKind.WEATHER_KIND_SANDSTORM;
            default -> WeatherKind.WEATHER_KIND_UNSPECIFIED;
        };
    }
}
