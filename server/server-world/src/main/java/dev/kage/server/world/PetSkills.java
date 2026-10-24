package dev.kage.server.world;

import java.util.random.RandomGenerator;

import dev.kage.server.world.component.CombatStatsComponent;

/**
 * Kỹ năng linh thú trên ĐÒN ĐÁNH THƯỜNG (ADR-086) — hàm thuần, chạy trên tick thread trong {@link CombatSystem}. Bên
 * đánh
 * có linh thú "đánh thêm" thì có xác suất đòn trúng gây thêm 50%; bên nhận có linh thú "hộ chủ" thì có xác suất chắn
 * nửa
 * sát thương. Chiêu thức (SkillSystem) không đi qua đây ở bản đầu.
 */
final class PetSkills {

    private static final int PERMILLE = 1000;
    private static final int STRIKE_BONUS_PERCENT = 50;
    private static final int PERCENT = 100;

    private PetSkills() {}

    static CombatConstants.AttackResult apply(
            CombatConstants.AttackResult result,
            CombatStatsComponent attacker,
            CombatStatsComponent target,
            RandomGenerator random) {
        int damage = result.damage();
        if (damage <= 0) {
            return result; // trượt / né — không có gì để khuếch đại hay chắn
        }
        if (attacker.petStrikePermille > 0 && random.nextInt(PERMILLE) < attacker.petStrikePermille) {
            damage += damage * STRIKE_BONUS_PERCENT / PERCENT;
        }
        if (target.petGuardPermille > 0 && random.nextInt(PERMILLE) < target.petGuardPermille) {
            damage = Math.max(1, damage / 2);
        }
        return damage == result.damage() ? result : new CombatConstants.AttackResult(false, result.crit(), damage);
    }
}
