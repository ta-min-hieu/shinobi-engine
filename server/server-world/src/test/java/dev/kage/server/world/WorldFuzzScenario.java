package dev.kage.server.world;

import java.util.List;

/**
 * Ba "loại World" mà bộ kiểm thuộc tính ({@link WorldSimulationPropertyTest}) lần lượt chạy — đúng ba
 * cách {@code server-gateway} dựng {@link WorldFacade}: world chính (có quái/NPC/vùng an toàn, hồi
 * sinh tại chỗ), hầm ngục (rỗng + boss qua {@code spawnBoss}, xác nằm yên) và đấu trường (rỗng, bật
 * PvP, xác nằm yên).
 *
 * <p>Nội dung dựng tay, KHÔNG phải số cân bằng thật: mục đích là phủ CƠ CHẾ (quái hung/thụ động/đánh
 * trả, boss có script, vùng an toàn hồi/không hồi, NPC), nên thời gian hồi sinh quái để ngắn (2s) cho
 * vòng chết–hồi sinh xảy ra nhiều lần trong một lượt chạy.
 */
enum WorldFuzzScenario {
    OVERWORLD,
    DUNGEON,
    ARENA;

    static final String HEALING_ZONE_CODE = "fuzz_sect_grounds";
    static final String SHELTER_ZONE_CODE = "fuzz_waypost";
    static final float HEALING_ZONE_MIN_X = -12f;
    static final float HEALING_ZONE_MAX_X = -2f;
    static final float RESPAWN_SECONDS = 2f;
    private static final String HUNTER_CODE = "fuzz_hunter";
    private static final String SENTRY_CODE = "fuzz_sentry";

    /** Dựng World CHƯA start — bộ kiểm tự tick tay ({@link WorldFacade#runOneTickOnCallerThread()}). */
    WorldFacade newWorld(WorldOutboundSink sink) {
        return switch (this) {
            case OVERWORLD -> new WorldFacade(sink, true, TickMetricsPort.NO_OP, overworldContent());
            case DUNGEON -> {
                WorldFacade world =
                        new WorldFacade(sink, false, TickMetricsPort.NO_OP, TestWorldContents.dungeonWithBoss());
                world.spawnBoss(TestMonsterCatalogs.SAMPLE_BOSS_CODE);
                yield world;
            }
            case ARENA -> {
                WorldFacade world = new WorldFacade(sink, false, TickMetricsPort.NO_OP, WorldContentCatalog.EMPTY);
                world.allowPlayerVersusPlayer();
                yield world;
            }
        };
    }

    /** World chính hồi sinh nhân vật tại chỗ — nhân vật không bao giờ còn nằm 0 máu sau một tick. */
    boolean respawnsCharacters() {
        return this == OVERWORLD;
    }

    /** Số điểm spawn quái (trần số quái thường sống cùng lúc). */
    int spawnPointCount() {
        return this == OVERWORLD ? overworldContent().monsters().spawnPoints().size() : 0;
    }

    /** Số NPC tĩnh world này đặt. */
    int npcCount() {
        return this == OVERWORLD ? overworldContent().npcs().placements().size() : 0;
    }

    /**
     * Quái hung dữ không đánh trả, quái đánh trả, cọc thụ động, và một BOSS đứng ở điểm spawn thường
     * (ADR-048) — cùng NPC trưởng lão, một vùng tông môn hồi máu và một trạm tiếp ứng không hồi.
     */
    static WorldContentCatalog overworldContent() {
        MonsterCatalogSnapshot.MonsterSpecies hunter = new MonsterCatalogSnapshot.MonsterSpecies(
                HUNTER_CODE,
                "mob_training-dummy",
                120,
                10L,
                new MonsterCatalogSnapshot.MonsterCombatNumbers(9, 0, 2, 2, 90, 5, 5, "NONE"),
                new MonsterCatalogSnapshot.MonsterAiNumbers(6f, 10f, 3f, 1.5f, 3f, 1f, true),
                null);
        MonsterCatalogSnapshot.MonsterSpecies sentry = new MonsterCatalogSnapshot.MonsterSpecies(
                SENTRY_CODE,
                "mob_training-dummy",
                60,
                10L,
                new MonsterCatalogSnapshot.MonsterCombatNumbers(6, 0, 0, 0, 100, 0, 0, "NONE"),
                new MonsterCatalogSnapshot.MonsterAiNumbers(0f, 10f, 3f, 1.5f, 0f, 3f, true),
                null);
        MonsterCatalogSnapshot base = TestMonsterCatalogs.trainingGround();
        MonsterCatalogSnapshot boss = TestMonsterCatalogs.dungeonBoss();
        MonsterCatalogSnapshot monsters = MonsterCatalogSnapshot.of(
                List.of(
                        base.find(TestMonsterCatalogs.TRAINING_DUMMY_CODE).orElseThrow(),
                        boss.find(TestMonsterCatalogs.SAMPLE_BOSS_CODE).orElseThrow(),
                        hunter,
                        sentry),
                List.of(
                        spawn(TestMonsterCatalogs.TRAINING_DUMMY_CODE, 8f),
                        spawn(HUNTER_CODE, 14f),
                        spawn(TestMonsterCatalogs.TRAINING_DUMMY_CODE, 21f),
                        spawn(SENTRY_CODE, 27f),
                        spawn(HUNTER_CODE, -20f),
                        spawn(TestMonsterCatalogs.SAMPLE_BOSS_CODE, 40f)));
        SafeZoneCatalogSnapshot zones = new SafeZoneCatalogSnapshot(List.of(
                new SafeZoneCatalogSnapshot.SafeZoneDefinition(
                        HEALING_ZONE_CODE, HEALING_ZONE_MIN_X, -10f, HEALING_ZONE_MAX_X, 10f, true),
                new SafeZoneCatalogSnapshot.SafeZoneDefinition(SHELTER_ZONE_CODE, 30f, -10f, 34f, 10f, false)));
        return new WorldContentCatalog(monsters, TestWorldContents.townElderOnly(), zones);
    }

    private static MonsterCatalogSnapshot.MonsterSpawnDefinition spawn(String code, float x) {
        return new MonsterCatalogSnapshot.MonsterSpawnDefinition(code, x, 0f, RESPAWN_SECONDS);
    }
}
