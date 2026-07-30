package dev.kage.server.world;

/**
 * Tham số skill — TOÀN BỘ hằng số ở đây là PLACEHOLDER, chưa cân bằng qua playtest (cùng tinh
 * thần {@link CombatConstants}). Theo GAMEPLAY.md §4, {@code cooldown_ms}/{@code chi_cost} đúng ra
 * nằm ở DB {@code skill_template} — nhưng module này CHƯA có bảng đó, giống {@code monster_template}
 * chưa DB-driven ({@code CombatConstants.MONSTER_STATS}); hardcode 1 skill duy nhất ở lát cắt này,
 * nối DB thật là việc của lát cắt sau khi có nhiều skill/{@code character_skill} (đã học skill nào).
 *
 * <p>{@link #FIREBALL_SCRIPT_RESOURCE} MỚI là nội dung thật theo Điều 27/ADR-016 — hệ số damage +
 * "đòn kết liễu" nằm trong script GraalJS, không hardcode Java ({@link SkillSystem}).
 */
final class SkillConstants {

    static final String FIREBALL_SKILL_CODE = "fireball";
    static final String FIREBALL_SCRIPT_RESOURCE = "/scripts/skill/fireball.js";
    static final float FIREBALL_COOLDOWN_SECONDS = 3f;
    static final int FIREBALL_CHI_COST = 10;
    static final float FIREBALL_RANGE_METERS = 5f;

    // placeholder — GDD chưa chốt công thức Khí tối đa hay cơ chế hồi Khí (chỉ trừ lúc cast, không hồi).
    static final int CHARACTER_MAX_CHI = 100;

    // Đủ dư cho 1 script tính damage vài dòng (if/return) — chặn vòng lặp vô hạn thật
    // (SCRIPTING.md §4, ScriptSandbox.statementLimit), không phải giới hạn "vừa đủ" cần tinh chỉnh.
    static final long SCRIPT_MAX_STATEMENTS = 10_000L;

    // GDD §5 "Tuyệt Kỹ Lv50" — gate ở SkillSystem.tryCast qua CharacterLevelComponent.
    static final int ULTIMATE_REQUIRED_LEVEL = 50;

    private SkillConstants() {}
}
