package dev.kage.server.world;

import java.util.List;

/**
 * Danh mục quái dựng tay cho test (ADR-037).
 *
 * <p>Trước ADR-037 những con số này là hằng số trong {@code CombatConstants}/{@code WorldFacade} nên
 * test mượn thẳng được. Nay chúng sống trong DB, mà {@code server-world} không được biết DB — nên
 * test phải TỰ dựng catalog. Đây là cái giá đã ghi trong Consequences của ADR đó.
 *
 * <p>{@link #trainingGround()} tái lập ĐÚNG nội dung world chính trước ADR-037 (4 hình nộm ở
 * 8/14/21/27, 50 máu, atk 8 / def 5 / res 3 / hit 90%, hồi sinh 10s) để mọi assertion cũ giữ nguyên
 * ý nghĩa: nếu một test đỏ sau đợt này thì đó là đổi HÀNH VI, không phải đổi số liệu.
 *
 * <p>⚠️ <b>{@link #AGGRESSIVE_AI} là fixture CƠ CHẾ, không còn khớp nội dung DB.</b> Từ
 * ADR-039 D4, {@code training_dummy} thật trong {@code monster_template} là loài THỤ ĐỘNG
 * (aggro = 0, xem {@link #passiveTrainingGround()}). Fixture ở đây giữ bộ tham số hung dữ mặc định
 * vì các test dùng nó kiểm <b>FSM Idle→Chase→Attack</b> — thứ độc lập với loài nào đang chạy nó.
 */
final class TestMonsterCatalogs {

    static final String TRAINING_DUMMY_CODE = "training_dummy";
    static final int TRAINING_DUMMY_MAX_HP = 50;
    static final float TRAINING_DUMMY_RESPAWN_SECONDS = 10f;

    /**
     * Bộ tham số AI mặc định của dự án trước ADR-039 (aggro 6m, leash 10m, đuổi 3 m/s, tuần tra
     * 1,5 m/s trong bán kính 3m, nghỉ 3s) — bằng ĐÚNG {@code AiConstants.DEFAULTS} và bằng đúng
     * các {@code DEFAULT} của migration V25.
     */
    static final MonsterCatalogSnapshot.MonsterAiNumbers AGGRESSIVE_AI =
            new MonsterCatalogSnapshot.MonsterAiNumbers(6f, 10f, 3f, 1.5f, 3f, 3f);

    private static final float[] SPAWN_X = {8f, 14f, 21f, 27f};

    private TestMonsterCatalogs() {}

    /** Nội dung world chính y như trước ADR-037. */
    static MonsterCatalogSnapshot trainingGround() {
        MonsterCatalogSnapshot.MonsterSpecies dummy = new MonsterCatalogSnapshot.MonsterSpecies(
                TRAINING_DUMMY_CODE,
                "mob_training-dummy",
                TRAINING_DUMMY_MAX_HP,
                20L,
                new MonsterCatalogSnapshot.MonsterCombatNumbers(8, 0, 5, 3, 90, 0, 0, "NONE"),
                AGGRESSIVE_AI,
                null); // không phải boss (ADR-048)
        List<MonsterCatalogSnapshot.MonsterSpawnDefinition> spawns = new java.util.ArrayList<>();
        for (float x : SPAWN_X) {
            spawns.add(new MonsterCatalogSnapshot.MonsterSpawnDefinition(
                    TRAINING_DUMMY_CODE, x, 0f, TRAINING_DUMMY_RESPAWN_SECONDS));
        }
        return MonsterCatalogSnapshot.of(List.of(dummy), spawns);
    }

    /**
     * Nội dung world chính ĐÚNG NHƯ DB SAU V25: hình nộm thụ động (ADR-039 D4) — aggro 0 nên nó
     * không bao giờ rời cọc, patrol 0 nên nó không lang thang.
     */
    static MonsterCatalogSnapshot passiveTrainingGround() {
        MonsterCatalogSnapshot.MonsterSpecies dummy = new MonsterCatalogSnapshot.MonsterSpecies(
                TRAINING_DUMMY_CODE,
                "mob_training-dummy",
                TRAINING_DUMMY_MAX_HP,
                20L,
                new MonsterCatalogSnapshot.MonsterCombatNumbers(8, 0, 5, 3, 90, 0, 0, "NONE"),
                new MonsterCatalogSnapshot.MonsterAiNumbers(0f, 10f, 3f, 1.5f, 0f, 3f),
                null); // không phải boss (ADR-048)
        return MonsterCatalogSnapshot.of(
                List.of(dummy),
                List.of(new MonsterCatalogSnapshot.MonsterSpawnDefinition(
                        TRAINING_DUMMY_CODE, SPAWN_X[0], 0f, TRAINING_DUMMY_RESPAWN_SECONDS)));
    }

    /** Mã boss Phase 8 — nguyên văn {@code BossConstants.BOSS_CODE} cũ, nay là một hàng V41. */
    static final String SAMPLE_BOSS_CODE = "sample_boss";

    /**
     * Danh mục CHỈ có boss — nội dung của một World hầm ngục (ADR-048).
     *
     * <p>Không có {@code MonsterSpawnDefinition} nào: boss vào world bằng lệnh
     * {@code spawnBoss(mã)}, không qua {@code monster_spawn} (ADR-048 D6). Trước ADR-048 World
     * hầm ngục chạy với catalog RỖNG vì chỉ số boss là hằng số Java; nay thiếu hàng này thì
     * {@code spawnBoss} tra không ra mã và hầm ngục rỗng.
     *
     * <p>Từng con số chép đúng V41, vốn chép đúng {@code CombatConstants.BOSS_STATS} +
     * {@code BOSS_MAX_HP} trước đó (ADR-048 D5) — nên test Phase 8 giữ nguyên kỳ vọng.
     */
    static MonsterCatalogSnapshot dungeonBoss() {
        MonsterCatalogSnapshot.MonsterSpecies boss = new MonsterCatalogSnapshot.MonsterSpecies(
                SAMPLE_BOSS_CODE,
                "mob_sample-boss",
                320,
                200L,
                new MonsterCatalogSnapshot.MonsterCombatNumbers(3, 0, 10, 8, 95, 0, 5, "NONE"),
                AGGRESSIVE_AI,
                new MonsterCatalogSnapshot.MonsterBossNumbers("/scripts/boss/sample_boss.js", 0.5f, 3.0f));
        return MonsterCatalogSnapshot.of(List.of(boss), List.of());
    }
}
