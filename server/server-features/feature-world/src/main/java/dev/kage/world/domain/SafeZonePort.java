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

    /**
     * ADR-137 — dải PvP tự do của một world (bảng {@code pvp_zone}, V112). Cùng port vì cùng là HÌNH HỌC BẢN ĐỒ nạp một
     * lần lúc khởi động; mặc định rỗng (fake trong test không phải khai).
     */
    default List<PvpZone> listPvpZones(String worldCode) {
        return List.of();
    }
}
