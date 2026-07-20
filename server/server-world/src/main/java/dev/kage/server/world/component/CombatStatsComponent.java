package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Chỉ số chiến đấu ĐÃ DERIVE (GDD §5) — gắn 1 lần lúc attach/spawn entity, KHÔNG tính lại mỗi tick
 * (chưa có trang bị/buff thay đổi được ở lát cắt này — GAMEPLAY.md §2 "tính lại khi đổi trang bị/
 * lên cấp/buff" là việc của lát cắt sau, khi các hệ đó tồn tại). Cả nhân vật lẫn quái đều có
 * component này để {@code CombatSystem} đọc đối xứng 2 phe, không hardcode ai là "nhân vật" ai là
 * "quái" (dọn đường cho PvP/nhiều loại quái sau này).
 */
public final class CombatStatsComponent implements Component {
    public int atk;
    public int def;
    public float hitPercent;
    public float dodgePercent;
    public float critPercent;
    public float critDamageMultiplier;
}
