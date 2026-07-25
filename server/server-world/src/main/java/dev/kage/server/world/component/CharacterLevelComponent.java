package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Cấp nhân vật lúc attach — CHỈ dùng để {@code SkillSystem} gate Tuyệt Kỹ (Lv50, GDD §5). Gắn 1
 * LẦN lúc attach (từ {@code Character.level()} DB), KHÔNG cập nhật giữa phiên — dự án chưa có cơ
 * chế lên cấp giữa phiên (chưa có hệ thống EXP-giết-quái, chỉ EXP qua quest), cùng hạn chế đã chấp
 * nhận với {@link CombatStatsComponent} (đăng nhập lại để cập nhật).
 */
public final class CharacterLevelComponent implements Component {
    public int level;
}
