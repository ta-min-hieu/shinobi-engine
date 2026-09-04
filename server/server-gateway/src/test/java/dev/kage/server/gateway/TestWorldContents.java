package dev.kage.server.gateway;

import java.util.List;

import dev.kage.server.world.NpcCatalogSnapshot;
import dev.kage.server.world.WorldContentCatalog;

/**
 * Nội dung tĩnh world chính dựng tay cho test (ADR-040) — quái ({@link TestMonsterCatalogs}) + NPC.
 *
 * <p>Bản sao của {@code TestWorldContents} bên {@code server-world} — hai module test khác nhau
 * không chia sẻ source set, đúng như các Fake Port vẫn được lặp lại ở mỗi module.
 *
 * <p>{@link #trainingGroundWithElder()} tái lập ĐÚNG nội dung world chính trước ADR-040 (một
 * {@code town_elder} ở {@code (-5, 0)}) để mọi assertion cũ giữ nguyên ý nghĩa.
 */
final class TestWorldContents {

    /** Nguyên văn {@code WorldFacade.SAMPLE_NPC_CODE} cũ, và hàng seed của migration V26. */
    static final String TOWN_ELDER_CODE = "town_elder";
    /** Nguyên văn {@code WorldFacade.SAMPLE_NPC_SPAWN_X} cũ. */
    static final float TOWN_ELDER_X = -5f;
    /** Nguyên văn {@code SAMPLE_NPC_SPAWN_Y} = {@code CHARACTER_PLAY_LINE_Y} cũ (ADR-033). */
    static final float TOWN_ELDER_Y = 0f;

    private TestWorldContents() {}

    /** Nội dung world chính y như trước ADR-037 + ADR-040. */
    static WorldContentCatalog trainingGroundWithElder() {
        return new WorldContentCatalog(TestMonsterCatalogs.trainingGround(), townElderOnly());
    }

    /** Chỉ trưởng lão, không quái — cho test chỉ quan tâm tới NPC. */
    static NpcCatalogSnapshot townElderOnly() {
        return new NpcCatalogSnapshot(
                List.of(new NpcCatalogSnapshot.NpcPlacementDefinition(TOWN_ELDER_CODE, TOWN_ELDER_X, TOWN_ELDER_Y)));
    }
}
