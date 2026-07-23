package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Đánh dấu entity là NPC (tĩnh, không AI, không combat) + mã định danh loại NPC (Phase 6 lát cắt
 * 1 — hardcode 1 NPC, chưa có {@code npc_template} DB, cùng tinh thần {@code MonsterComponent}
 * trước khi có {@code monster_template} spawn thật).
 */
public final class NpcComponent implements Component {
    public String npcCode;
}
