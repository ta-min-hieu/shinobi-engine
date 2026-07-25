package dev.kage.server.world;

/**
 * Tham số Boss AI (Phase 8 lát cắt 2) — TOÀN BỘ hằng số ở đây là PLACEHOLDER, chưa cân bằng qua
 * playtest (cùng tinh thần {@link CombatConstants}/{@link SkillConstants}). Đúng ra thuộc
 * {@code monster_template}/{@code boss_template} DB (GAMEPLAY.md §6) — module này CHƯA có bảng đó
 * (giống {@link CombatConstants#MONSTER_STATS}), hardcode 1 boss duy nhất ở lát cắt này.
 *
 * <p>{@link #BOSS_SCRIPT_RESOURCE} MỚI là nội dung thật theo Điều 27/ADR-016 — pha/chiêu đặc biệt
 * nằm trong script GraalJS, KHÔNG hardcode Java ({@link BossAiSystem}).
 */
final class BossConstants {

    static final String BOSS_CODE = "sample_boss";
    static final String BOSS_SCRIPT_RESOURCE = "/scripts/boss/sample_boss.js";

    // AI.md §9 "không giả định chạy mỗi tick" — hỏi script mỗi 0.5s, không mỗi tick (60Hz).
    static final float DECISION_INTERVAL_SECONDS = 0.5f;
    static final float HEAVY_STRIKE_COOLDOWN_SECONDS = 3f;

    // Đủ dư cho 1 script vài dòng if/return object literal — chặn vòng lặp vô hạn thật
    // (SCRIPTING.md §4, ScriptSandbox.statementLimit), không phải giới hạn "vừa đủ" cần tinh chỉnh.
    static final long SCRIPT_MAX_STATEMENTS = 10_000L;

    private BossConstants() {
    }
}
