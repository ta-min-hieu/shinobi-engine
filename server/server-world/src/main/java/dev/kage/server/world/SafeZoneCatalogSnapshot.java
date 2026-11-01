package dev.kage.server.world;

import java.util.List;

/**
 * Vùng an toàn của world (ADR-046 D3) — dữ liệu THUẦN, không biết DB là gì.
 *
 * <p>Cùng lý do tồn tại như {@link MonsterCatalogSnapshot}/{@link NpcCatalogSnapshot}:
 * {@code server-world} không được đọc DB (Điều 23), nên {@code server-gateway} đọc bảng
 * {@code safe_zone} ({@code feature-world}) rồi dịch sang record này.
 *
 * <p><b>{@link #EMPTY} nghĩa là world không có vùng an toàn nào</b> — hành vi của dungeon/arena.
 *
 * @param zones vùng theo THỨ TỰ; hai vùng chồng nhau là hợp lệ (mỗi vùng vào/ra độc lập)
 */
public record SafeZoneCatalogSnapshot(List<SafeZoneDefinition> zones, List<OpenPvpRange> pvpRanges) {

    /** Không có vùng an toàn nào — dungeon/arena, và mọi test không quan tâm tới vùng an toàn. */
    public static final SafeZoneCatalogSnapshot EMPTY = new SafeZoneCatalogSnapshot(List.of(), List.of());

    public SafeZoneCatalogSnapshot {
        zones = List.copyOf(zones);
        pvpRanges = List.copyOf(pvpRanges);
    }

    /** Chỉ vùng an toàn, không dải PvP nào — hình dạng cũ (trước ADR-137). */
    public SafeZoneCatalogSnapshot(List<SafeZoneDefinition> zones) {
        this(zones, List.of());
    }

    /** ADR-137 — một dải PvP tự do trên trục X (bảng {@code pvp_zone}). */
    public record OpenPvpRange(String code, float minX, float maxX) {}

    public boolean isEmpty() {
        return zones.isEmpty();
    }

    /**
     * Một vùng an toàn hình chữ nhật: mã định danh + biên.
     *
     * <p>Tên "an toàn" chứ không phải "hồi máu": cùng vùng đó sau này là chỗ cấm PvP (STORY §7 —
     * tông môn là đất an toàn). ADR-046 chỉ thi hành phần hồi máu; chưa có cột "vùng này làm gì"
     * vì mới có đúng MỘT luật, và đoán trước luật thứ hai là đoán sai (xem Revisit Trigger).
     */
    public record SafeZoneDefinition(String code, float minX, float minY, float maxX, float maxY, boolean heals) {}
}
