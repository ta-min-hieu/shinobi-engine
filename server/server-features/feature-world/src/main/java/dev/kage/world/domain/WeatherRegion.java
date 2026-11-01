package dev.kage.world.domain;

import java.time.Instant;

/**
 * Một vùng thời tiết (ADR-144, bảng {@code weather_region}) — chỉ hiển thị. Trời đổi tất định theo đồng hồ: vùng đang
 * có thời tiết khi {@code (phút epoch + offset) mod period < active}.
 *
 * @param kind {@code SPIRIT_RAIN} hoặc {@code SANDSTORM}
 */
public record WeatherRegion(
        String code, float minX, float maxX, String kind, int periodMinutes, int activeMinutes, int offsetMinutes) {

    private static final long SECONDS_PER_MINUTE = 60L;

    /** Vùng này có đang có thời tiết lúc {@code now} không. */
    public boolean isActive(Instant now) {
        long minute = Math.floorDiv(now.getEpochSecond(), SECONDS_PER_MINUTE) + offsetMinutes;
        return Math.floorMod(minute, (long) periodMinutes) < activeMinutes;
    }
}
