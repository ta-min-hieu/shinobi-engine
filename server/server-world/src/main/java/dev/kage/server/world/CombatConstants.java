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
 * công thức GDD §5. Chỉ số quái (kể cả boss) KHÔNG ở đây: đọc từ {@code monster_template} rồi tới
 * server-world qua {@link MonsterCatalogSnapshot} (ADR-037, ADR-048).
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

    /**
     * Bản công khai-trong-package của {@link #CRIT_DAMAGE_MULTIPLIER} để {@code WorldFacade} dựng
     * chỉ số quái đọc từ DB (ADR-037). {@code monster_template} CHƯA có cột riêng cho hệ số này —
     * mọi loài dùng chung 150% như trước, thêm cột khi có loài thật cần khác đi.
     */
    static final float CRIT_DAMAGE_MULTIPLIER_DEFAULT = CRIT_DAMAGE_MULTIPLIER;

    private static final float MIN_HIT_PERCENT = 0.5f;
    private static final float MAX_HIT_PERCENT = 1.0f;
    private static final float MAX_DODGE_PERCENT = 0.3f;
    private static final float MAX_CRIT_PERCENT = 0.5f;
    private static final int MIN_DAMAGE_FLOOR = 1; // tránh damage=0 vô nghĩa khi giáp/kháng rất cao

    // Chỉ số quái thường KHÔNG còn ở đây từ ADR-037, và chỉ số BOSS cũng thôi từ ADR-048: cả hai
    // đọc từ `monster_template` rồi tới server-world qua MonsterCatalogSnapshot. `BOSS_STATS` và
    // `BOSS_MAX_HP` cũ đã chuyển nguyên trạng thành một hàng dữ liệu (V41) — xem ADR-048 D5.

    static final float ATTACK_INTERVAL_SECONDS = 1f; // ATK_SPEED placeholder — 1 đòn/giây
    static final float ATTACK_RANGE_METERS = 2f;
    /**
     * Cooldown coi như HẾT khi phần còn lại không quá nửa tick. Trừ {@code 1f/60f} kiểu float 60 lần
     * từ {@code 1f} còn dư ~2,8e-7 &gt; 0 ⇒ so với 0 thì đòn 1 giây thực ra 61 tick (~1,017 s), và client
     * giữ phím lặp đúng 1 s đôi khi tới sớm một tick rồi bị bỏ (đo 2026-10-01). Nửa tick đủ nuốt sai số
     * float mà không bao giờ làm cooldown ngắn đi trọn một tick (chủ dự án duyệt 2026-10-01).
     */
    static final float COOLDOWN_READY_EPSILON_SECONDS = WorldConstants.FIXED_DT / 2f;
    // MONSTER_MAX_HP và MONSTER_RESPAWN_SECONDS đã chuyển thành cột `monster_template.max_hp` và
    // `monster_spawn.respawn_seconds` (ADR-037) — mỗi điểm spawn có nhịp hồi sinh riêng.

    // ---------------------------------------------------------------------------------------
    // Hồi máu ngoài chiến đấu — ADR-046 D2. Chỉ áp cho NHÂN VẬT; quái lành lại bằng đường khác
    // (về tới điểm spawn thì đầy máu, AiSystem — ADR-046 D4).
    // ---------------------------------------------------------------------------------------

    /** Bao lâu không đánh ai VÀ không bị ai đánh thì bắt đầu hồi máu. */
    static final float REGEN_DELAY_SECONDS = 8f;

    /**
     * Phần máu TỐI ĐA hồi mỗi giây khi đã ngoài chiến đấu — tỉ lệ chứ không phải số tuyệt đối, vì
     * máu tối đa không còn là hằng số kể từ ADR-036 ({@code bonus_hp} của trang bị nâng nó lên).
     * Với 118 máu: ~3,5 máu/giây ⇒ 38→118 mất ~23 giây.
     */
    static final float REGEN_FRACTION_PER_SECOND = 0.03f;

    /**
     * Trần phát {@code HealthChangedEvent} của hồi máu: nhiều nhất 1 gói/giây/entity, và CHỈ khi
     * máu thực sự đổi ⇒ người đang đầy máu phát 0 gói. Con số này là cam kết với ADR-042 (đang
     * muốn GIẢM số gói) — không phải tham số cân bằng gameplay.
     */
    static final float REGEN_EMIT_INTERVAL_SECONDS = 1f;

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
