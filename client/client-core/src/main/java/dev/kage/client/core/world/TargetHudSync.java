package dev.kage.client.core.world;

import dev.kage.client.core.ui.hud.CombatHud;

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

    static void sync(CombatHudController combat, CombatHud hud) {
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
}
