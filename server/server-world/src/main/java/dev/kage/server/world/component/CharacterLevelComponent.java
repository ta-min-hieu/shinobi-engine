package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Cấp nhân vật mà luật TRONG world đọc — hiện chỉ {@code SkillSystem} dùng để gate Tuyệt Kỹ (Lv50,
 * GDD §5). Gắn lúc attach (từ {@code Character.level()} DB) và được NÂNG giữa phiên khi nhân vật
 * lên cấp nhờ EXP hạ quái hoặc thưởng quest: gateway gọi {@code WorldFacade.enqueueCharacterLevel},
 * lệnh chạy trên tick thread và không bao giờ hạ cấp (lệnh đến muộn bị bỏ qua).
 *
 * <p>Khác {@link CombatStatsComponent}: chỉ số chiến đấu không phụ thuộc cấp nên không có gì phải
 * tính lại khi lên cấp.
 */
public final class CharacterLevelComponent implements Component {
    public int level;
}
