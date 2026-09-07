package dev.kage.server.world;

/**
 * Toàn bộ nội dung tĩnh mà một world được phép đẻ ra lúc khởi động (ADR-040 D6) — quái, NPC và
 * vùng an toàn (ADR-046 D3).
 *
 * <p><b>Vì sao gói lại thay vì hai tham số:</b> constructor {@link WorldFacade} đã dùng HẾT 4 tham
 * số, đúng trần Checkstyle {@code ParameterNumber} (ADR-037 D3 ghi rõ nó "chạm đúng trần, không
 * vượt"). Không còn chỗ cho tham số thứ năm, nên tham số cuối mang cả hai danh mục. Đợt sau (tham
 * số AI theo loài, bệ/địa hình từ DB) vì thế không đâm vào cùng bức tường.
 *
 * <p>Hai danh mục vẫn là hai record riêng chứ không bị trộn thành một: quái có chỉ số chiến đấu,
 * bảng rơi, thời gian hồi sinh; NPC không có gì trong ba thứ đó (ADR-040 Alternatives E).
 *
 * @param monsters loài quái + điểm spawn ({@link MonsterCatalogSnapshot#EMPTY} = không quái nào)
 * @param npcs NPC tĩnh ({@link NpcCatalogSnapshot#EMPTY} = không NPC nào)
 * @param safeZones vùng an toàn ({@link SafeZoneCatalogSnapshot#EMPTY} = không vùng nào)
 */
public record WorldContentCatalog(
        MonsterCatalogSnapshot monsters, NpcCatalogSnapshot npcs, SafeZoneCatalogSnapshot safeZones) {

    /** World rỗng — dungeon/arena, và mọi test không quan tâm tới nội dung tĩnh. */
    public static final WorldContentCatalog EMPTY = new WorldContentCatalog(
            MonsterCatalogSnapshot.EMPTY, NpcCatalogSnapshot.EMPTY, SafeZoneCatalogSnapshot.EMPTY);

    public WorldContentCatalog {
        if (monsters == null || npcs == null || safeZones == null) {
            throw new IllegalArgumentException(
                    "dùng MonsterCatalogSnapshot.EMPTY/NpcCatalogSnapshot.EMPTY/SafeZoneCatalogSnapshot.EMPTY,"
                            + " không null");
        }
    }

    /** Chỉ có quái, không NPC/vùng an toàn — dùng cho test combat/AI và mọi chỗ hai thứ kia không liên quan. */
    public static WorldContentCatalog ofMonsters(MonsterCatalogSnapshot monsters) {
        return new WorldContentCatalog(monsters, NpcCatalogSnapshot.EMPTY, SafeZoneCatalogSnapshot.EMPTY);
    }
}
