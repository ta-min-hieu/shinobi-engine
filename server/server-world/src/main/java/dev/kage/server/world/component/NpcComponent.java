package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Đánh dấu entity là NPC (tĩnh, không AI, không combat) + mã định danh loại NPC.
 *
 * <p>{@code npcCode} khớp {@code npc_template.code} và đi thẳng ra client qua
 * {@code NpcSpawnedEvent} — client dùng nó cho cả khoá art ({@code NpcModelKey}) lẫn
 * {@code TalkToNpcCommand}. Từ ADR-040 nó đọc từ {@code npc_spawn} chứ không còn là hằng số
 * {@code WorldFacade.SAMPLE_NPC_CODE}, đúng như {@code MonsterComponent} đã thoát ra từ ADR-037.
 */
public final class NpcComponent implements Component {
    public String npcCode;
}
