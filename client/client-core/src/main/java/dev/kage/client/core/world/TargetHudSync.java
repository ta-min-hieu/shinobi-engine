package dev.kage.client.core.world;

import dev.kage.client.core.ui.hud.CombatHud;
import dev.kage.engine.ecs.component.PositionComponent;

/**
 * Đẩy mục tiêu hiện tại của {@link CombatHudController} xuống khung mục tiêu của {@link CombatHud},
 * mỗi khung render. Mục tiêu là QUÁI hoặc NHÂN VẬT (đối thủ đấu trường, 2026-10-04 — xem
 * {@link DuelTargeting}); hai loại hiện khác nhau ở tên và ở chỗ máu nhân vật có thể chưa biết.
 *
 * <p>Tách khỏi {@code WorldScreen} THUẦN vì trần {@code CyclomaticComplexity} cấp lớp của PMD (80),
 * không phải đổi hành vi. Không cấp phát trên đường nhân vật (chỉ đọc số nguyên + chuỗi có sẵn;
 * {@link CombatHud} chỉ {@code setText} khi giá trị đổi — UI.md §9).
 */
final class TargetHudSync {

    private TargetHudSync() {}

    static void sync(CombatHudController combat, CombatHud hud, PositionComponent local) {
        PickablePoint point = combat.targetPointPerFrame(); // ADR-299 — khoảng cách tới mục tiêu
        hud.setTargetDistance(point == null ? -1 : meters(point.x() - local.x, point.y() - local.y));
        if (combat.hasCharacterTarget()) {
            hud.setCharacterTarget(
                    combat.characterTargetName(), combat.characterTargetHp(), combat.characterTargetMaxHp());
            return;
        }
        CombatHudController.MonsterState target = combat.targetState();
        if (target != null) {
            hud.setTarget(target.code(), target.hp(), target.maxHp());
        } else {
            hud.clearTarget();
        }
    }

    /** Khoảng cách làm tròn tới mét (đơn vị thế giới = mét). Tĩnh để test. */
    static int meters(float dx, float dy) {
        return Math.round((float) Math.hypot(dx, dy));
    }
}
