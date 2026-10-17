package dev.kage.world.api;

import java.util.List;

import dev.kage.world.domain.SafeZone;
import dev.kage.world.domain.SafeZonePort;

/**
 * API công khai của {@code feature-world} — module khác ({@code server-gateway}) CHỈ gọi qua đây,
 * không đọc bảng {@code safe_zone} trực tiếp (Điều 24).
 *
 * <p>Đọc-thuần, không có use case nào: tra cứu config nạp một lần lúc khởi động, cùng khuôn
 * {@code MonsterFacade}. Module này KHÔNG biết {@code server-world} tồn tại — gateway là nơi dịch
 * {@link SafeZone} sang {@code SafeZoneCatalogSnapshot}.
 *
 * <p>Tên "geometry" chứ không phải "safe zone": đây là chỗ ở của HÌNH HỌC BẢN ĐỒ nói chung. Bệ nhảy
 * ({@code WorldFacade.DEMO_PLATFORMS}) và dải nền đất (đang là hằng số phía CLIENT — nguồn của bug
 * quái đứng lơ lửng 2026-09-06) là hai thứ tiếp theo thuộc về đây.
 */
public final class WorldGeometryFacade {

    private final SafeZonePort safeZonePort;

    /** Dựng ở composition root. */
    public WorldGeometryFacade(SafeZonePort safeZonePort) {
        this.safeZonePort = safeZonePort;
    }

    /**
     * Vùng an toàn của {@code worldCode}.
     *
     * @return rỗng nếu world đó không khai vùng nào (dungeon/arena) — không phải lỗi
     */
    public List<SafeZone> listSafeZones(String worldCode) {
        return safeZonePort.listSafeZones(worldCode);
    }
}
