package dev.kage.dialogue.domain;

/**
 * Một NPC đứng ở đâu (ADR-040 D1, bảng {@code npc_spawn}) — thay ba hằng số
 * {@code WorldFacade.SAMPLE_NPC_CODE}/{@code SAMPLE_NPC_SPAWN_X}/{@code SAMPLE_NPC_SPAWN_Y}.
 *
 * <p>Bảng riêng chứ không phải cột trên {@code npc_template} vì {@code code} là <b>danh tính trên
 * dây</b> ({@code TalkToNpcCommand.npc_code}): gộp vị trí vào đó khoá cứng "danh tính ⇔ vị trí"
 * thành 1:1 vĩnh viễn, nên cùng một NPC ở hai nơi buộc phải nhân bản cả script lẫn khoá i18n lẫn
 * model. Lập luận đầy đủ + phương án một-bảng đã cân: ADR-040 D1 và Alternatives A.
 *
 * <p><b>KHÔNG có {@code respawnSeconds}</b> — khác {@code MonsterSpawnPoint} một cách có chủ đích:
 * NPC không chết, {@code WorldFacade.spawnNpc()} nằm ngoài toàn bộ đường hồi sinh của quái.
 *
 * <p>{@code posY} luôn 0 (ADR-033 khoá di chuyển vào trục X — NPC đặt lệch y là NPC người chơi
 * không bao giờ tới đủ gần để nói chuyện); DB có {@code CHECK} bắt buộc điều đó, đây là hàng rào
 * thứ hai.
 *
 * @param npcCode {@code npc_template.code} — đi thẳng ra client qua {@code NpcSpawnedEvent}
 * @param worldCode {@link #MAIN_WORLD_CODE} cho world chính
 */
public record NpcPlacement(String npcCode, String worldCode, float posX, float posY) {

    /** World chính — dungeon/arena không đặt NPC nào (ADR-040 D1 lý do 2). */
    public static final String MAIN_WORLD_CODE = "main";

    public NpcPlacement {
        requireText(npcCode, "npcCode");
        requireText(worldCode, "worldCode");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " không được rỗng");
        }
    }
}
