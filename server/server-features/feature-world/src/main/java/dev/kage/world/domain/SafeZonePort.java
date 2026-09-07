package dev.kage.world.domain;

import java.util.List;

/** Đọc {@code safe_zone} — adapter thật ở {@code server-database}. */
public interface SafeZonePort {

    /**
     * Vùng an toàn của một world.
     *
     * @param worldCode {@link SafeZone#MAIN_WORLD_CODE} cho world chính
     * @return rỗng nếu world đó không khai vùng nào — hợp lệ, không phải lỗi (dungeon/arena)
     */
    List<SafeZone> listSafeZones(String worldCode);
}
