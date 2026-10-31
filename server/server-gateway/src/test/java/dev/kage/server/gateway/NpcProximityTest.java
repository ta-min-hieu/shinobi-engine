package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.character.domain.PositionSnapshot;
import dev.kage.server.world.NpcCatalogSnapshot;

/**
 * Hình học của {@link NpcProximity} (quyết định chủ dự án 2026-10-11): tầm 5 m quanh NPC, cộng 5 m bù
 * cho vị trí gateway biết có thể chậm một nhịp lấy mẫu.
 */
class NpcProximityTest {

    private static final long CHARACTER_ID = 1L;
    private static final String ELDER = "town_elder";
    private static final String ATTENDANT = "waypost_attendant";

    private static final float REACH = NpcProximity.INTERACTION_RANGE_METERS + NpcProximity.POSITION_SLACK_METERS;

    private static final NpcCatalogSnapshot NPCS = new NpcCatalogSnapshot(List.of(
            new NpcCatalogSnapshot.NpcPlacementDefinition(ELDER, -5f, 0f),
            new NpcCatalogSnapshot.NpcPlacementDefinition(ATTENDANT, 100f, 0f),
            new NpcCatalogSnapshot.NpcPlacementDefinition(ATTENDANT, 300f, 0f)));

    @Test
    void shouldAccept_atTheEdgeOfReach_andRefuseJustBeyondIt() {
        assertThat(standingAt(-5f + REACH).isWithinReach(CHARACTER_ID, ELDER)).isTrue();
        assertThat(standingAt(-5f + REACH + 0.5f).isWithinReach(CHARACTER_ID, ELDER))
                .isFalse();
        assertThat(standingAt(-5f - REACH).isWithinReach(CHARACTER_ID, ELDER)).isTrue();
    }

    /** Cùng một mã đặt ở nhiều trạm: đứng cạnh trạm NÀO cũng nói chuyện được. */
    @Test
    void shouldUseTheNearestPlacement_whenTheSameNpcStandsInSeveralSpots() {
        assertThat(standingAt(298f).isWithinReach(CHARACTER_ID, ATTENDANT)).isTrue();
        assertThat(standingAt(102f).isWithinReach(CHARACTER_ID, ATTENDANT)).isTrue();
        assertThat(standingAt(200f).isWithinReach(CHARACTER_ID, ATTENDANT)).isFalse();
    }

    /** Đứng cạnh NPC này không cho phép nói chuyện với NPC KHÁC ở xa. */
    @Test
    void shouldJudgeEachNpcByItsOwnSpot() {
        NpcProximity besideTheElder = standingAt(-5f);

        assertThat(besideTheElder.isWithinReach(CHARACTER_ID, ELDER)).isTrue();
        assertThat(besideTheElder.isWithinReach(CHARACTER_ID, ATTENDANT)).isFalse();
    }

    @Test
    void shouldRefuseAnNpcThatIsNotPlacedInTheWorld() {
        assertThat(standingAt(0f).isWithinReach(CHARACTER_ID, "senior_sister")).isFalse();
    }

    /** Đang trong hầm ngục/đấu trường: không có vị trí ở world chính, không NPC nào ở gần. */
    @Test
    void shouldRefuseEveryNpc_whenTheCharacterIsNotInTheMainWorld() {
        NpcProximity inAnInstance = new NpcProximity(NPCS, characterId -> Optional.empty());

        assertThat(inAnInstance.isWithinReach(CHARACTER_ID, ELDER)).isFalse();
    }

    /**
     * ADR-131 — NPC động (thương nhân du hành) không nằm trong catalog tĩnh: thiếu sổ NPC động thì luôn "quá xa"
     * (kiểm sống 2026-10-31 bắt đúng lỗi này). Sổ đọc lại mỗi lần hỏi, nên gỡ xong là hết với tới.
     */
    @Test
    void shouldReachADynamicNpcOnlyWhileItIsPlaced() {
        java.util.concurrent.atomic.AtomicReference<List<NpcCatalogSnapshot.NpcPlacementDefinition>> placed =
                new java.util.concurrent.atomic.AtomicReference<>(
                        List.of(new NpcCatalogSnapshot.NpcPlacementDefinition("traveling_merchant", -3f, 0f)));
        NpcProximity proximity =
                new NpcProximity(NPCS, characterId -> Optional.of(new PositionSnapshot(-3f, 0f)), placed::get);

        assertThat(proximity.isWithinReach(CHARACTER_ID, "traveling_merchant")).isTrue();
        placed.set(List.of());
        assertThat(proximity.isWithinReach(CHARACTER_ID, "traveling_merchant")).isFalse();
    }

    private static NpcProximity standingAt(float x) {
        return new NpcProximity(NPCS, characterId -> Optional.of(new PositionSnapshot(x, 0f)));
    }
}
