package dev.kage.server.world;

import dev.kage.server.world.component.CombatStatsComponent;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chỉ số + công thức damage — TOÀN BỘ hệ số ở đây là PLACEHOLDER, chưa cân bằng qua playtest
 * (GAME-DESIGN-DOCUMENT.md §5-6, cùng tinh thần {@code MOVE_SPEED_MPS}/{@code AOI_RADIUS_METERS}
 * ở {@link WorldConstants}). Đặt tập trung ở 1 nơi — không hardcode rải rác trong
 * {@link CombatSystem}/{@link SkillSystem} (CODING-STANDARDS.md §18).
 *
 * <p>{@link #deriveCombatStats} tính ATK/SKILL_DMG/DEF/RES/HIT%/DODGE%/CRIT% THẬT từ
 * {@code character_stat} DB (qua {@link CharacterBaseStats}, gateway truyền vào lúc attach) theo
 * công thức GDD §5. Quái vẫn dùng {@link #MONSTER_STATS} cố định — chưa có {@code monster_template}
 * DB-driven (lát cắt sau).
 *
 * <p>{@link #resolveAttack} là đòn đánh thường (vật lý, ATK vs DEF). {@link #resolveSkillAttack}
 * là skill (phép, SKILL_DMG vs RES — GDD §6 "damage phép tính y hệt, thay DEF bằng RES") — Java lo
 * cơ chế (miss/crit/mitigation, giống hệt đòn thường), SCRIPT lo nội dung (hệ số/bonus riêng từng
 * skill, {@link SkillSystem}, Điều 27/ADR-016). Hệ số khắc chế vẫn cố định 1.0 (quái chưa có hệ phái).
 */
final class CombatConstants {

    /** Chỉ số chiến đấu 1 phe — dùng chung cho cả nhân vật lẫn quái (GDD §5). */
    record CombatStats(int atk, int skillDmg, int def, int res, float hitPercent, float dodgePercent,
            float critPercent, float critDamageMultiplier) {
    }

    /** Kết quả 1 đòn đánh/skill đã tính xong — {@code damage == 0} khi {@code miss}. */
    record AttackResult(boolean miss, boolean crit, int damage) {
    }

    static final int CHARACTER_MAX_HP = 100; // placeholder — GDD chưa chốt công thức HP tối đa

    // Hệ số derive GDD §5 — placeholder, chưa playtest.
    private static final float CRIT_DAMAGE_MULTIPLIER = 1.5f; // 150% cố định ở MVP
    private static final float MIN_HIT_PERCENT = 0.5f;
    private static final float MAX_HIT_PERCENT = 1.0f;
    private static final float MAX_DODGE_PERCENT = 0.3f;
    private static final float MAX_CRIT_PERCENT = 0.5f;
    private static final int MIN_DAMAGE_FLOOR = 1; // tránh damage=0 vô nghĩa khi giáp/kháng rất cao

    // Quái bù nhìn TỰ ĐÁNH TRẢ khi bị AiSystem đưa vào Attack (AI.md §3) — atk/def placeholder,
    // chưa có monster_template DB-driven (lát cắt sau mới per-loại-quái). skillDmg=0 vì quái chưa
    // cast skill ở lát cắt này; res placeholder cùng độ lớn với def (làm mục tiêu hợp lệ cho skill).
    static final CombatStats MONSTER_STATS = new CombatStats(8, 0, 5, 3, 0.90f, 0f, 0f, CRIT_DAMAGE_MULTIPLIER);

    static final float ATTACK_INTERVAL_SECONDS = 1f; // ATK_SPEED placeholder — 1 đòn/giây
    static final float ATTACK_RANGE_METERS = 2f;
    static final int MONSTER_MAX_HP = 50;
    static final float MONSTER_RESPAWN_SECONDS = 10f;

    private CombatConstants() {
    }

    /**
     * Derive ATK/SKILL_DMG/DEF/RES/HIT%/DODGE%/CRIT% từ STR/AGI/INT/VIT (GAME-DESIGN-DOCUMENT.md
     * §5, bảng "Công thức derive").
     */
    static CombatStats deriveCombatStats(CharacterBaseStats baseStats) {
        int atk = (baseStats.str() * 2) + baseStats.agi();
        int skillDmg = Math.round(baseStats.intelligence() * 2.5f);
        int def = Math.round((baseStats.vit() * 1.5f) + (baseStats.str() * 0.5f));
        int res = Math.round(baseStats.vit() + (baseStats.intelligence() * 0.5f));
        float hitPercent = clamp((90f + (baseStats.agi() * 0.1f)) / 100f, MIN_HIT_PERCENT, MAX_HIT_PERCENT);
        float dodgePercent = clamp((baseStats.agi() * 0.15f) / 100f, 0f, MAX_DODGE_PERCENT);
        float critPercent = clamp((baseStats.agi() * 0.1f) / 100f, 0f, MAX_CRIT_PERCENT);
        return new CombatStats(atk, skillDmg, def, res, hitPercent, dodgePercent, critPercent, CRIT_DAMAGE_MULTIPLIER);
    }

    /**
     * Pipeline damage ĐÃ CHỐT (GAME-DESIGN-DOCUMENT.md §6): né → damage thô → bạo kích → giảm
     * giáp kiểu LMHT/Liên Quân (diminishing returns) → hệ số khắc chế (=1.0, chưa áp dụng ở lát
     * cắt này). Random chỉ sinh ở server (ENGINE.md §10) — {@link ThreadLocalRandom}, KHÔNG bao
     * giờ nhận giá trị né/bạo kích từ client.
     */
    static AttackResult resolveAttack(CombatStats attacker, CombatStats defender) {
        float hitChance = clamp(attacker.hitPercent() - defender.dodgePercent(), MIN_HIT_PERCENT, MAX_HIT_PERCENT);
        if (ThreadLocalRandom.current().nextFloat() >= hitChance) {
            return new AttackResult(true, false, 0);
        }

        boolean crit = ThreadLocalRandom.current().nextFloat() < attacker.critPercent();
        float rawDamage = attacker.atk() * (crit ? attacker.critDamageMultiplier() : 1f);

        float effectiveDef = Math.max(0f, defender.def());
        float mitigation = 100f / (100f + effectiveDef);

        int finalDamage = Math.max(MIN_DAMAGE_FLOOR, Math.round(rawDamage * mitigation));
        return new AttackResult(false, crit, finalDamage);
    }

    /**
     * Damage skill (GDD §6 "damage phép tính y hệt, thay DEF bằng RES") — {@code scriptRawDamage}
     * là damage thô bước 2 (SKILL_DMG × hệ số + bonus riêng skill) đã tính SẴN bởi script
     * ({@link SkillSystem}, Điều 27); hàm này chỉ làm phần MIỆN (miss/crit/mitigation RES), y hệt
     * cơ chế {@link #resolveAttack} — Java lo cơ chế dùng chung cho mọi skill, không lặp lại ở
     * từng script.
     */
    static AttackResult resolveSkillAttack(CombatStats attacker, CombatStats defender, double scriptRawDamage) {
        float hitChance = clamp(attacker.hitPercent() - defender.dodgePercent(), MIN_HIT_PERCENT, MAX_HIT_PERCENT);
        if (ThreadLocalRandom.current().nextFloat() >= hitChance) {
            return new AttackResult(true, false, 0);
        }

        boolean crit = ThreadLocalRandom.current().nextFloat() < attacker.critPercent();
        float rawDamage = (float) scriptRawDamage * (crit ? attacker.critDamageMultiplier() : 1f);

        float effectiveRes = Math.max(0f, defender.res());
        float mitigation = 100f / (100f + effectiveRes);

        int finalDamage = Math.max(MIN_DAMAGE_FLOOR, Math.round(rawDamage * mitigation));
        return new AttackResult(false, crit, finalDamage);
    }

    /**
     * Component ECS (mutable) → record bất biến pipeline damage cần — dùng chung
     * {@link CombatSystem}/{@link SkillSystem}.
     */
    static CombatStats toStats(CombatStatsComponent c) {
        return new CombatStats(
                c.atk, c.skillDmg, c.def, c.res, c.hitPercent, c.dodgePercent, c.critPercent, c.critDamageMultiplier);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
