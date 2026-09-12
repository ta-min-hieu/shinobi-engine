package dev.kage.server.gateway;

import java.util.List;

import dev.kage.server.world.MonsterCatalogSnapshot;

/**
 * Danh mục quái dựng tay cho test (ADR-037).
 *
 * <p>Bản sao của {@code TestMonsterCatalogs} bên {@code server-world} — hai module test khác nhau
 * không chia sẻ source set, đúng như các Fake Port vẫn được lặp lại ở mỗi module.
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

    /** Mã boss hầm ngục mẫu — phải khớp {@code DungeonInstanceRegistry.DUNGEON_BOSS_CODE}. */
    static final String SAMPLE_BOSS_CODE = "sample_boss";

    /**
     * Sân tập KÈM boss hầm ngục (ADR-048).
     *
     * <p>Trước ADR-048, World hầm ngục dựng với catalog RỖNG vẫn spawn được boss vì chỉ số boss là
     * hằng số Java. Nay boss là một hàng {@code monster_template}: catalog thiếu boss ⇒ hầm ngục
     * rỗng và ba test Phase 8 đỏ. Đây là chỗ duy nhất nói ra điều đó cho mọi test gateway.
     *
     * <p>Boss KHÔNG có {@code MonsterSpawnDefinition}: hầm ngục spawn hắn bằng lệnh
     * {@code spawnBoss(mã)} chứ không qua {@code monster_spawn} (ADR-048 D6).
     */
    static MonsterCatalogSnapshot trainingGroundWithBoss() {
        MonsterCatalogSnapshot ground = trainingGround();
        List<MonsterCatalogSnapshot.MonsterSpecies> species =
                new java.util.ArrayList<>(ground.species().values());
        species.add(sampleBoss());
        return MonsterCatalogSnapshot.of(species, ground.spawnPoints());
    }

    /**
     * Boss Phase 8 — từng con số chép đúng V41, vốn chép đúng hằng số Java trước đó (ADR-048 D5).
     *
     * <p>{@code atk = 3} KHÔNG phải lỗi gõ: sát thương của hắn đến gần như toàn bộ từ CHIÊU trong
     * script ({@code base_atk} nhân hệ số), không phải từ đánh thường.
     */
    private static MonsterCatalogSnapshot.MonsterSpecies sampleBoss() {
        return new MonsterCatalogSnapshot.MonsterSpecies(
                SAMPLE_BOSS_CODE,
                "mob_sample-boss",
                320,
                200L,
                new MonsterCatalogSnapshot.MonsterCombatNumbers(3, 0, 10, 8, 95, 0, 5, "NONE"),
                AGGRESSIVE_AI,
                new MonsterCatalogSnapshot.MonsterBossNumbers("/scripts/boss/sample_boss.js", 0.5f, 3.0f));
    }
}
