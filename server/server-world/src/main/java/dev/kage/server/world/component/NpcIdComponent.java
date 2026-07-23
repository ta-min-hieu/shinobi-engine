package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Gắn {@code npcId} (ÂM, server-world tự cấp) vào entity ECS của NPC — đối xứng
 * {@code MonsterIdComponent}. NPC không tham gia combat/skill nên KHÔNG cần
 * {@code WorldFacade.lookupEntity} nhận diện được.
 *
 * <p>⚠️ {@code npcId} DÙNG CHUNG 1 bộ đếm với {@code monsterId} ({@code WorldFacade.nextMonsterOrNpcId})
 * — KHÔNG phải 2 bộ đếm độc lập. Lý do (phát hiện thật, không phải thiết kế trước): 2 bộ đếm riêng
 * cùng bắt đầu ở -1 khiến quái/NPC có thể trùng số, và {@code AreaOfInterestTracker.visibleTo} dùng
 * CHUNG 1 {@code Set<Long>} theo ID bất kể loại entity — trùng số làm 2 entity khác loại ghi đè
 * trạng thái "đang thấy" của nhau, xem javadoc {@code WorldFacade.nextMonsterOrNpcId}.
 */
public final class NpcIdComponent implements Component {
    public long npcId;
}
