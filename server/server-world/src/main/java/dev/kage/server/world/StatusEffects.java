package dev.kage.server.world;

import dev.kage.server.world.component.StatusEffect;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.StatusEffectType;

/**
 * Helper tĩnh đọc/ghi {@link StatusEffectComponent} — tập trung mọi phép tính liên quan buff/debuff
 * ở 1 nơi (cùng tinh thần {@link CombatConstants}), dùng chung bởi {@link CombatSystem},
 * {@link SkillSystem}, {@link WorldFacade}. KHÔNG sửa {@code CombatStatsComponent} gốc — mọi
 * modifier tính lại lúc resolve (giữ nguyên tắc "chỉ số dẫn xuất gắn 1 lần" của
 * {@code CombatStatsComponent}).
 */
final class StatusEffects {

    private static final float MIN_CRIT_PERCENT = 0f;

    private StatusEffects() {}

    static boolean isStunned(StatusEffectComponent status) {
        for (StatusEffect effect : status.slots) {
            if (effect != null && effect.type == StatusEffectType.STUN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hệ số nhân tốc độ di chuyển từ các hiệu ứng đang có.
     *
     * @return hệ số nhân tốc độ di chuyển, luôn &gt;= 0 (nhiều {@code MOVE_SPEED_MOD} cộng dồn).
     */
    static float moveSpeedMultiplier(StatusEffectComponent status) {
        float multiplier = 1f;
        for (StatusEffect effect : status.slots) {
            if (effect != null && effect.type == StatusEffectType.MOVE_SPEED_MOD) {
                multiplier += effect.magnitude;
            }
        }
        return Math.max(0f, multiplier);
    }

    /** Cộng %DEF/%RES/%CRIT đang active vào {@code base} — KHÔNG mutate {@code base}. */
    static CombatConstants.CombatStats applyModifiers(CombatConstants.CombatStats base, StatusEffectComponent status) {
        float defMod = 0f;
        float resMod = 0f;
        float critMod = 0f;
        for (StatusEffect effect : status.slots) {
            if (effect == null) {
                continue;
            }
            switch (effect.type) {
                case DEF_MOD -> defMod += effect.magnitude;
                case RES_MOD -> resMod += effect.magnitude;
                case CRIT_MOD -> critMod += effect.magnitude;
                default -> {
                    // STUN/SHIELD/MOVE_SPEED_MOD không ảnh hưởng CombatStats — xử lý riêng.
                }
            }
        }
        if (defMod == 0f && resMod == 0f && critMod == 0f) {
            return base;
        }
        float clampedCrit =
                Math.max(MIN_CRIT_PERCENT, Math.min(CombatConstants.MAX_CRIT_PERCENT, base.critPercent() + critMod));
        return new CombatConstants.CombatStats(
                base.atk(),
                base.skillDmg(),
                Math.round(base.def() * (1f + defMod)),
                Math.round(base.res() * (1f + resMod)),
                base.hitPercent(),
                base.dodgePercent(),
                clampedCrit,
                base.critDamageMultiplier(),
                base.element());
    }

    /** Trừ damage vào shield TRƯỚC — @return phần damage còn lại sau khi hấp thụ (>= 0). */
    static int absorbWithShield(StatusEffectComponent status, int rawDamage) {
        StatusEffect[] slots = status.slots;
        for (int i = 0; i < slots.length; i++) {
            StatusEffect effect = slots[i];
            if (effect != null && effect.type == StatusEffectType.SHIELD) {
                int absorbed = Math.min(rawDamage, Math.round(effect.magnitude));
                effect.magnitude -= absorbed;
                if (effect.magnitude <= 0f) {
                    slots[i] = null;
                }
                return rawDamage - absorbed;
            }
        }
        return rawDamage;
    }

    /**
     * Ghi 1 hiệu ứng vào slot trống (hoặc REFRESH nếu mục tiêu đã có CÙNG loại — tránh chồng 2 buff
     * cùng loại). Hết slot trống + không có cùng loại để refresh → bỏ qua ÂM THẦM, đúng pattern
     * chung của {@code SkillSystem}/{@code CombatSystem} khi điều kiện không thoả.
     */
    static void applyEffect(
            StatusEffectComponent status, StatusEffectType type, float magnitude, float durationSeconds) {
        StatusEffect[] slots = status.slots;
        int freeSlot = -1;
        for (int i = 0; i < slots.length; i++) {
            StatusEffect effect = slots[i];
            if (effect != null && effect.type == type) {
                slots[i] = new StatusEffect(type, magnitude, durationSeconds);
                return;
            }
            if (effect == null && freeSlot == -1) {
                freeSlot = i;
            }
        }
        if (freeSlot != -1) {
            slots[freeSlot] = new StatusEffect(type, magnitude, durationSeconds);
        }
    }
}
