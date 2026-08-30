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
 */
final class TestMonsterCatalogs {

    static final String TRAINING_DUMMY_CODE = "training_dummy";
    static final int TRAINING_DUMMY_MAX_HP = 50;
    static final float TRAINING_DUMMY_RESPAWN_SECONDS = 10f;

    private static final float[] SPAWN_X = {8f, 14f, 21f, 27f};

    private TestMonsterCatalogs() {}

    /** Nội dung world chính y như trước ADR-037. */
    static MonsterCatalogSnapshot trainingGround() {
        MonsterCatalogSnapshot.MonsterSpecies dummy = new MonsterCatalogSnapshot.MonsterSpecies(
                TRAINING_DUMMY_CODE,
                "mob_training-dummy",
                TRAINING_DUMMY_MAX_HP,
                20L,
                new MonsterCatalogSnapshot.MonsterCombatNumbers(8, 0, 5, 3, 90, 0, 0, "NONE"));
        List<MonsterCatalogSnapshot.MonsterSpawnDefinition> spawns = new java.util.ArrayList<>();
        for (float x : SPAWN_X) {
            spawns.add(new MonsterCatalogSnapshot.MonsterSpawnDefinition(
                    TRAINING_DUMMY_CODE, x, 0f, TRAINING_DUMMY_RESPAWN_SECONDS));
        }
        return MonsterCatalogSnapshot.of(List.of(dummy), spawns);
    }
}
