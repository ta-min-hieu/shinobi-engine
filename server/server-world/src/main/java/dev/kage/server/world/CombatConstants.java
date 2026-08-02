package dev.kage.server.world;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import dev.kage.server.world.component.CombatStatsComponent;

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
    record CombatStats(
            int atk,
            int skillDmg,
            int def,
            int res,
            float hitPercent,
            float dodgePercent,
            float critPercent,
            float critDamageMultiplier,
            Element element) {}

    /** Kết quả 1 đòn đánh/skill đã tính xong — {@code damage == 0} khi {@code miss}. */
    record AttackResult(boolean miss, boolean crit, int damage) {}

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
    static final CombatStats MONSTER_STATS =
            new CombatStats(8, 0, 5, 3, 0.90f, 0f, 0f, CRIT_DAMAGE_MULTIPLIER, Element.NONE);

    // Boss (Phase 8 lát cắt 2, BossAiSystem) — def/res cao hơn MONSTER_STATS (boss "trâu" hơn),
    // atk dùng làm ctx.base_atk cho script sample_boss.js tính damage chiêu đặc biệt (script
    // KHÔNG dùng skillDmg — boss là hệ vật lý ở lát cắt này, chưa gán hệ phái).
    //
    // ⚠️ atk CỐ Ý thấp (không "cân bằng boss thật") — PHÁT HIỆN THẬT (test thời gian thực nhiều
    // pha): CHARACTER_MAX_HP=100 là hằng số CỐ ĐỊNH toàn cục; atk cao hơn khiến nhân vật test chết
    // TRƯỚC khi boss chết (AiSystem đưa boss về RETURN khi mục tiêu chết → BossAiSystem vĩnh viễn
    // không còn quyết định gì, dù CombatSystem của nhân vật đã chết vẫn tiếp tục đánh trả — bug có
    // sẵn, ngoài phạm vi lát cắt này). Giữ atk thấp để trận đấu 1-boss-1-nhân-vật luôn có người
    // thắng đúng phía (nhân vật) trong lúc chưa có cơ chế tử vong nhân vật.
    static final CombatStats BOSS_STATS =
            new CombatStats(3, 0, 10, 8, 0.95f, 0f, 0.05f, CRIT_DAMAGE_MULTIPLIER, Element.NONE);
    // Đủ lớn để MỖI pha (đặc biệt pha enrage <30% HP) có vài giây thời gian thực trước khi boss
    // chết — PHÁT HIỆN THẬT: 180 quá thấp, đòn hạ gục thường rơi ĐÚNG vào lúc vừa chạm ngưỡng 30%,
    // BossAiSystem (throttle 0.5s) không kịp có cơ hội quyết định trước khi entity bị xoá. Vẫn đủ
    // nhỏ để trận đấu thời gian thực xong trong ~12s (test thật, không mock tick) — cùng lý do
    // MONSTER_RESPAWN_SECONDS chọn nhỏ cho test, không phải số cân bằng gameplay.
    static final int BOSS_MAX_HP = 320;

    static final float ATTACK_INTERVAL_SECONDS = 1f; // ATK_SPEED placeholder — 1 đòn/giây
    static final float ATTACK_RANGE_METERS = 2f;
    static final int MONSTER_MAX_HP = 50;
    static final float MONSTER_RESPAWN_SECONDS = 10f;

    private CombatConstants() {}

    /** Bonus passive TĨNH theo hệ phái (GDD §5 "2 passive/hệ") — cộng THẲNG vào chỉ số derive,
     * KHÔNG dùng script (Điều 27 chỉ bắt buộc script cho NỘI DUNG skill, passive chỉ là số cố định
     * giống các hằng số khác trong lớp này). NONE = 0 (quái/boss chưa gán hệ phái). */
    private record PassiveBonus(
            int atk,
            int skillDmg,
            int def,
            int res,
            float hitPercent,
            float dodgePercent,
            float critPercent,
            float critDamageMultiplier) {
        private static final PassiveBonus NONE_BONUS = new PassiveBonus(0, 0, 0, 0, 0f, 0f, 0f, 0f);
    }

    private static final Map<Element, PassiveBonus> FACTION_PASSIVES = Map.of(
            // Hỏa: "sát thương liên tục, bùng nổ" (GDD §5 dòng 60) — nuker thuần, không CC/khiên/hồi
            // phục nên dồn cả 2 passive vào tấn công: SKILL_DMG cao hơn + tỉ lệ bạo kích cao hơn (dễ
            // nổ crit hơn = "bùng nổ").
            Element.HOA, new PassiveBonus(0, 6, 0, 0, 0f, 0f, 0.05f, 0f),
            Element.NONE, PassiveBonus.NONE_BONUS,
            // Thủy: "khống chế, hồi phục" — né tốt hơn (linh hoạt) + kháng phép tốt hơn (dai sức).
            Element.THUY, new PassiveBonus(0, 0, 0, 5, 0f, 0.05f, 0f, 0f),
            // Phong: "tốc độ, bạo kích" — chính xác + bạo kích cao hơn.
            Element.PHONG, new PassiveBonus(0, 0, 0, 0, 0.05f, 0f, 0.05f, 0f),
            // Thổ: "phòng thủ, khiên" — giáp + kháng phép đều cao hơn.
            Element.THO, new PassiveBonus(0, 0, 6, 6, 0f, 0f, 0f, 0f),
            // Lôi: "đơn mục tiêu, xuyên giáp" — chính xác cao + đòn bạo kích nặng hơn.
            Element.LOI, new PassiveBonus(0, 0, 0, 0, 0.03f, 0f, 0f, 0.2f));

    /**
     * Derive ATK/SKILL_DMG/DEF/RES/HIT%/DODGE%/CRIT% từ STR/AGI/INT/VIT (GAME-DESIGN-DOCUMENT.md
     * §5, bảng "Công thức derive"), cộng thêm bonus passive tĩnh theo hệ phái ({@link #FACTION_PASSIVES}).
     */
    static CombatStats deriveCombatStats(CharacterBaseStats baseStats) {
        int atk = (baseStats.str() * 2) + baseStats.agi();
        int skillDmg = Math.round(baseStats.intelligence() * 2.5f);
        int def = Math.round((baseStats.vit() * 1.5f) + (baseStats.str() * 0.5f));
        int res = Math.round(baseStats.vit() + (baseStats.intelligence() * 0.5f));
        float hitPercent = clamp((90f + (baseStats.agi() * 0.1f)) / 100f, MIN_HIT_PERCENT, MAX_HIT_PERCENT);
        float dodgePercent = clamp((baseStats.agi() * 0.15f) / 100f, 0f, MAX_DODGE_PERCENT);
        float critPercent = clamp((baseStats.agi() * 0.1f) / 100f, 0f, MAX_CRIT_PERCENT);

        PassiveBonus bonus = FACTION_PASSIVES.getOrDefault(baseStats.element(), PassiveBonus.NONE_BONUS);
        return new CombatStats(
                atk + bonus.atk(),
                skillDmg + bonus.skillDmg(),
                def + bonus.def(),
                res + bonus.res(),
                clamp(hitPercent + bonus.hitPercent(), MIN_HIT_PERCENT, MAX_HIT_PERCENT),
                clamp(dodgePercent + bonus.dodgePercent(), 0f, MAX_DODGE_PERCENT),
                clamp(critPercent + bonus.critPercent(), 0f, MAX_CRIT_PERCENT),
                CRIT_DAMAGE_MULTIPLIER + bonus.critDamageMultiplier(),
                baseStats.element());
    }

    /**
     * Pipeline damage ĐÃ CHỐT (GAME-DESIGN-DOCUMENT.md §6): né → damage thô → bạo kích → giảm
     * giáp kiểu LMHT/Liên Quân (diminishing returns) → hệ số khắc chế nguyên tố (Phase 8, 4 hệ
     * phái, {@link ElementCounterTable}). Random chỉ sinh ở server (ENGINE.md §10) —
     * {@link ThreadLocalRandom}, KHÔNG bao giờ nhận giá trị né/bạo kích từ client.
     */
    static AttackResult resolveAttack(CombatStats attacker, CombatStats defender) {
        float hitChance = clamp(attacker.hitPercent() - defender.dodgePercent(), MIN_HIT_PERCENT, MAX_HIT_PERCENT);
        if (ThreadLocalRandom.current().nextFloat() >= hitChance) {
            return new AttackResult(true, false, 0);
        }

        boolean crit = ThreadLocalRandom.current().nextFloat() < attacker.critPercent();
        float rawDamage = attacker.atk() * (crit ? attacker.critDamageMultiplier() : 1f);
        rawDamage *= ElementCounterTable.multiplier(attacker.element(), defender.element());

        float effectiveDef = Math.max(0f, defender.def());
        float mitigation = 100f / (100f + effectiveDef);

        int finalDamage = Math.max(MIN_DAMAGE_FLOOR, Math.round(rawDamage * mitigation));
        return new AttackResult(false, crit, finalDamage);
    }

    /**
     * Damage skill (GDD §6 "damage phép tính y hệt, thay DEF bằng RES") — {@code scriptRawDamage}
     * là damage thô bước 2 (SKILL_DMG × hệ số + bonus riêng skill) đã tính SẴN bởi script
     * ({@link SkillSystem}, Điều 27); hàm này chỉ làm phần MIỆN (miss/crit/mitigation RES/khắc chế
     * nguyên tố), y hệt cơ chế {@link #resolveAttack} — Java lo cơ chế dùng chung cho mọi skill,
     * không lặp lại ở từng script. Không xuyên giáp (hệ Lôi) — xem overload 4 tham số.
     */
    static AttackResult resolveSkillAttack(CombatStats attacker, CombatStats defender, double scriptRawDamage) {
        return resolveSkillAttack(attacker, defender, scriptRawDamage, 0f);
    }

    /**
     * @param armorPiercePercent 0-1, % RES hiệu dụng của defender bị bỏ qua TRƯỚC mitigation (hệ
     *     Lôi "xuyên giáp", GDD §4) — script trả về qua field {@code armor_pierce}, 0 nếu skill
     *     không có cơ chế này (mọi hệ khác).
     */
    static AttackResult resolveSkillAttack(
            CombatStats attacker, CombatStats defender, double scriptRawDamage, float armorPiercePercent) {
        float hitChance = clamp(attacker.hitPercent() - defender.dodgePercent(), MIN_HIT_PERCENT, MAX_HIT_PERCENT);
        if (ThreadLocalRandom.current().nextFloat() >= hitChance) {
            return new AttackResult(true, false, 0);
        }

        boolean crit = ThreadLocalRandom.current().nextFloat() < attacker.critPercent();
        float rawDamage = (float) scriptRawDamage * (crit ? attacker.critDamageMultiplier() : 1f);
        rawDamage *= ElementCounterTable.multiplier(attacker.element(), defender.element());

        float effectiveRes = Math.max(0f, defender.res() * (1f - clamp(armorPiercePercent, 0f, 1f)));
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
                c.atk,
                c.skillDmg,
                c.def,
                c.res,
                c.hitPercent,
                c.dodgePercent,
                c.critPercent,
                c.critDamageMultiplier,
                c.element);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
