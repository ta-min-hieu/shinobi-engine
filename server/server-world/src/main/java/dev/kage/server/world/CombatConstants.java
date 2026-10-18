package dev.kage.server.world;

import java.util.Map;
import java.util.random.RandomGenerator;

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

    /**
     * Damage thô của một skill do script tính sẵn, kèm xuyên giáp.
     *
     * @param scriptRawDamage damage thô bước 2 (SKILL_DMG × hệ số + bonus riêng skill).
     * @param armorPiercePercent 0-1, % RES hiệu dụng của defender bị bỏ qua TRƯỚC mitigation (hệ
     *     Lôi "xuyên giáp", GDD §4) — script trả về qua field {@code armor_pierce}, 0 nếu skill
     *     không có cơ chế này (mọi hệ khác).
     */
    record SkillDamage(double scriptRawDamage, float armorPiercePercent) {}

    /** Máu tối đa ở Cấp 1, chưa mặc gì — nền của {@link #characterMaxHp}. */
    static final int CHARACTER_MAX_HP = 100;

    /**
     * Máu tối đa tăng thêm mỗi cấp — dòng 65(d), chủ dự án chọn 2026-10-17: trước đó lên cấp không
     * cộng chỉ số nào, máu 100 từ Cấp 1 tới Cấp 100 (chỉ trang bị đổi được nó).
     *
     * <p>2 chứ không phải 5 của đề xuất ban đầu: đo bằng các test cân bằng, 5 làm Lv30 lên 351 máu
     * (206 trước đó) và xoá cổng trang bị của arc 1–2 (bộ Tạp Dịch thắng đầu lĩnh đoạt mạch). 2 giữ
     * mọi cổng dưới luật biên thật ({@code DuelMargin}) và chỉ nới đều mỗi trận một–hai nhát.
     */
    static final int CHARACTER_HP_PER_LEVEL = 2;

    /**
     * {@code maxHp = 100 + 2 × (cấp − 1) + bonus_hp} (dòng 65(d); phần {@code bonus_hp} từ ADR-036).
     * Cấp &lt; 1 tính như Cấp 1; kết quả không bao giờ dưới 1.
     */
    static int characterMaxHp(int level, int bonusHp) {
        return Math.max(1, CHARACTER_MAX_HP + levelHpGain(1, level) + bonusHp);
    }

    /** Máu tối đa có thêm khi đi từ cấp {@code from} lên cấp {@code to} (không âm, cấp &lt; 1 tính là 1). */
    static int levelHpGain(int from, int to) {
        return CHARACTER_HP_PER_LEVEL * Math.max(0, Math.max(1, to) - Math.max(1, from));
    }

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

    /**
     * Trần chí mạng cho TỔNG (chỉ số gốc + trang bị + buff). Chủ dự án chốt 2026-10-11 (README dòng
     * 37iv): buff không được đẩy vượt trần của chỉ số gốc — {@link StatusEffects#applyModifiers} kẹp
     * theo đúng hằng số này.
     */
    static final float MAX_CRIT_PERCENT = 0.5f;

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

    // Hệ số Lực Chiến — GDD §5, placeholder (chỉ để HIỂN THỊ/XẾP HẠNG, không bao giờ tính damage).
    private static final double CP_PER_HP = 1.0;
    private static final double CP_PER_CHI = 0.5;
    private static final double CP_PER_OFFENSE = 3.5;
    private static final double CP_PER_DEFENSE = 3.0;
    private static final double CP_PER_CRIT_POINT = 2.0;
    private static final double CP_PER_DODGE_POINT = 1.5;
    private static final double CP_PER_HIT_POINT = 0.5;
    private static final double CP_PER_LEVEL = 50.0;
    private static final float PERMILLE = 1000f;
    private static final float PERCENT = 100f;

    /**
     * Bảng chỉ số + Lực Chiến (ADR-056) từ chỉ số nền + trang bị — CÙNG phép cộng {@code applyBonusTo}
     * của World (ATK/DEF/RES cộng bonus trang bị, máu tối đa qua {@link #characterMaxHp}).
     */
    static CharacterStatSheet statSheet(CharacterBaseStats base, EquipmentBonusSnapshot bonus, int maxChi) {
        CombatStats derived = deriveCombatStats(base);
        int maxHp = characterMaxHp(base.level(), bonus.hp());
        int atk = derived.atk() + bonus.atk();
        int def = derived.def() + bonus.def();
        int res = derived.res() + bonus.res();
        // Lực Chiến GDD §5: HP + Chi×0,5 + (ATK+SKILL)×3,5 + (DEF+RES)×3 + CRIT%×100×2 + DODGE%×100×1,5
        // + HIT%×100×0,5 + ATK_SPEED_bonus%×100×1,5 (chưa có bonus tốc đánh ⇒ 0) + Level×50. Làm tròn xuống.
        double power = maxHp * CP_PER_HP
                + maxChi * CP_PER_CHI
                + (atk + derived.skillDmg()) * CP_PER_OFFENSE
                + (def + res) * CP_PER_DEFENSE
                + derived.critPercent() * PERCENT * CP_PER_CRIT_POINT
                + derived.dodgePercent() * PERCENT * CP_PER_DODGE_POINT
                + derived.hitPercent() * PERCENT * CP_PER_HIT_POINT
                + Math.max(1, base.level()) * CP_PER_LEVEL;
        return new CharacterStatSheet(
                base.level(),
                base.str(),
                base.agi(),
                base.intelligence(),
                base.vit(),
                maxHp,
                bonus.hp(),
                maxChi,
                atk,
                derived.skillDmg(),
                def,
                res,
                Math.round(derived.hitPercent() * PERMILLE),
                Math.round(derived.dodgePercent() * PERMILLE),
                Math.round(derived.critPercent() * PERMILLE),
                Math.round(derived.critDamageMultiplier() * PERCENT),
                (long) Math.floor(power));
    }

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
     * phái, {@link ElementCounterTable}). Random chỉ sinh ở server (ENGINE.md §10) — {@code random}
     * do World tiêm vào ({@link ThreadLocalRandomSource} ở production, seed cố định ở test), KHÔNG
     * bao giờ nhận giá trị né/bạo kích từ client.
     */
    static AttackResult resolveAttack(CombatStats attacker, CombatStats defender, RandomGenerator random) {
        float hitChance = clamp(attacker.hitPercent() - defender.dodgePercent(), MIN_HIT_PERCENT, MAX_HIT_PERCENT);
        if (random.nextFloat() >= hitChance) {
            return new AttackResult(true, false, 0);
        }

        boolean crit = random.nextFloat() < attacker.critPercent();
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
     * không lặp lại ở từng script. Không xuyên giáp (hệ Lôi) — xem overload nhận {@link SkillDamage}.
     */
    static AttackResult resolveSkillAttack(
            CombatStats attacker, CombatStats defender, double scriptRawDamage, RandomGenerator random) {
        return resolveSkillAttack(attacker, defender, new SkillDamage(scriptRawDamage, 0f), random);
    }

    /** Như overload {@code double} nhưng có xuyên giáp — xem {@link SkillDamage#armorPiercePercent()}. */
    static AttackResult resolveSkillAttack(
            CombatStats attacker, CombatStats defender, SkillDamage skillDamage, RandomGenerator random) {
        float hitChance = clamp(attacker.hitPercent() - defender.dodgePercent(), MIN_HIT_PERCENT, MAX_HIT_PERCENT);
        if (random.nextFloat() >= hitChance) {
            return new AttackResult(true, false, 0);
        }

        boolean crit = random.nextFloat() < attacker.critPercent();
        float armorPiercePercent = skillDamage.armorPiercePercent();
        float rawDamage = (float) skillDamage.scriptRawDamage() * (crit ? attacker.critDamageMultiplier() : 1f);
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
