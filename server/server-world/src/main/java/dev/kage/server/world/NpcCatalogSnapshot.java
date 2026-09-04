package dev.kage.server.world;

import java.util.List;

/**
 * Danh sách NPC tĩnh mà world này đặt (ADR-040 D6) — dữ liệu THUẦN, không biết DB là gì.
 *
 * <p>Cùng lý do tồn tại như {@link MonsterCatalogSnapshot}: {@code server-world} không được đọc DB
 * (Điều 23) và {@code server-database} thậm chí không phụ thuộc {@code server-world}; chỉ
 * {@code server-gateway} phụ thuộc cả hai, nên gateway là nơi đọc {@code npc_spawn} rồi dịch sang
 * record này.
 *
 * <p><b>{@link #EMPTY} nghĩa là world không có NPC nào</b> — đó là hành vi của dungeon/arena.
 *
 * @param placements NPC theo THỨ TỰ; NPC không chết nên không có khái niệm "hồi sinh đúng chỗ cũ",
 *     tức chỉ số trong danh sách này KHÔNG mang ý nghĩa như {@code MonsterCatalogSnapshot}
 */
public record NpcCatalogSnapshot(List<NpcPlacementDefinition> placements) {

    /** Không có NPC nào — dungeon/arena, và mọi test không quan tâm tới NPC. */
    public static final NpcCatalogSnapshot EMPTY = new NpcCatalogSnapshot(List.of());

    public NpcCatalogSnapshot {
        placements = List.copyOf(placements);
    }

    public boolean isEmpty() {
        return placements.isEmpty();
    }

    /**
     * Một NPC được đặt sẵn: mã trên dây và chỗ đứng.
     *
     * <p>Không có chỉ số chiến đấu, không có máu, không có thời gian hồi sinh — NPC tĩnh không di
     * chuyển, không đánh nhau, không chết ({@code WorldFacade#spawnNpc}). Khoá art KHÔNG nằm ở đây
     * có chủ đích: client tự suy bằng phép biến đổi thuần ({@code NpcModelKey}), và ADR-040 D5 trả
     * lời "không" cho việc thêm {@code model_key} — thêm cột mà không gửi qua dây là một cột không
     * ai đọc, còn gửi qua dây là đổi protocol để giải một vấn đề chưa tồn tại.
     */
    public record NpcPlacementDefinition(String npcCode, float posX, float posY) {}
}
