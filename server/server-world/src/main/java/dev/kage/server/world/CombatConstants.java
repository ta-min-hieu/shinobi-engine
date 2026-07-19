package dev.kage.server.world;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Chỉ số + công thức damage — TOÀN BỘ hệ số ở đây là PLACEHOLDER, chưa cân bằng qua playtest
 * (GAME-DESIGN-DOCUMENT.md §5-6, cùng tinh thần {@code MOVE_SPEED_MPS}/{@code AOI_RADIUS_METERS}
 * ở {@link WorldConstants}). Đặt tập trung ở 1 nơi — không hardcode rải rác trong
 * {@link CombatSystem} (CODING-STANDARDS.md §18).
 *
 * <p>⚠️ Lát cắt 1 Phase 4: {@link #CHARACTER_STATS} là số CỐ ĐỊNH cho MỌI nhân vật — CHƯA đọc
 * {@code character_stat} thật từ DB rồi derive theo công thức GDD §5 (STR·AGI·INT·VIT → ATK/DEF).
 * Đó là việc lớn riêng (feature-character chưa có Port nào đọc stats), để lát cắt sau.
 *
 * <p>Chỉ có đánh thường (vật lý, ATK vs DEF) — chưa có SKILL_DMG/RES (đó là cho
 * {@code UseSkillCommand}, chờ ADR-016 script sandbox) và hệ số khắc chế cố định 1.0 (quái chưa
 * có hệ phái).
 */
final class CombatConstants {

    /** Chỉ số chiến đấu 1 phe — dùng chung cho cả nhân vật lẫn quái (GDD §5). */
    record CombatStats(int atk, int def, float hitPercent, float dodgePercent, float critPercent,
            float critDamageMultiplier) {
    }

    /** Kết quả 1 đòn đánh đã tính xong — {@code damage == 0} khi {@code miss}. */
    record AttackResult(boolean miss, boolean crit, int damage) {
    }

    static final CombatStats CHARACTER_STATS = new CombatStats(20, 10, 0.95f, 0.05f, 0.10f, 1.5f);
    static final int CHARACTER_MAX_HP = 100;

    // Quái bù nhìn KHÔNG tự đánh trả (lát cắt này chưa có AI) — atk=0 vô hại nếu logic sau này
    // vô tình cho nó tấn công, không phải cách chặn chính (Family không gồm quái vì không có
    // Target/AttackCooldown — xem CombatSystem).
    static final CombatStats MONSTER_STATS = new CombatStats(0, 5, 0.90f, 0f, 0f, 1.5f);

    static final float ATTACK_INTERVAL_SECONDS = 1f; // ATK_SPEED placeholder — 1 đòn/giây
    static final float ATTACK_RANGE_METERS = 2f;
    static final int MONSTER_MAX_HP = 50;
    static final float MONSTER_RESPAWN_SECONDS = 10f;

    private static final float MIN_HIT_PERCENT = 0.5f;
    private static final float MAX_HIT_PERCENT = 1.0f;
    private static final int MIN_DAMAGE_FLOOR = 1; // tránh damage=0 vô nghĩa khi giáp rất cao

    private CombatConstants() {
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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
