package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.Faction;

/** ADR-132 — bậc trang phục theo cảnh giới: B1 Luyện Khí, B2 từ Trúc Cơ (Cấp 19), B3 từ Tử Phủ (Cấp 37). */
class CharacterAppearanceTest {

    @Test
    void shouldClimbCostumeTiersAtTheRealmBoundaries() {
        assertThat(CharacterAppearance.tierFor(1)).isEqualTo(CostumeTier.COSTUME_TIER_B1);
        assertThat(CharacterAppearance.tierFor(18)).isEqualTo(CostumeTier.COSTUME_TIER_B1);
        assertThat(CharacterAppearance.tierFor(19)).isEqualTo(CostumeTier.COSTUME_TIER_B2);
        assertThat(CharacterAppearance.tierFor(36)).isEqualTo(CostumeTier.COSTUME_TIER_B2);
        assertThat(CharacterAppearance.tierFor(37)).isEqualTo(CostumeTier.COSTUME_TIER_B3);
        assertThat(CharacterAppearance.tierFor(100)).isEqualTo(CostumeTier.COSTUME_TIER_B3);
    }

    @Test
    void shouldCarryTheFactionAndTheTierOfTheLevel() {
        assertThat(CharacterAppearance.of(Faction.FACTION_LOI, 44))
                .isEqualTo(new CharacterAppearance(Faction.FACTION_LOI, CostumeTier.COSTUME_TIER_B3, 44));
    }

    @Test
    void shouldCountRealmsFromTheirOpeningLevels() {
        assertThat(CharacterAppearance.realmIndex(18)).isZero();
        assertThat(CharacterAppearance.realmIndex(19)).isEqualTo(1);
        assertThat(CharacterAppearance.realmIndex(54)).isEqualTo(2);
        assertThat(CharacterAppearance.realmIndex(55)).isEqualTo(3);
        assertThat(CharacterAppearance.realmIndex(100)).isEqualTo(5);
    }

    /** ADR-140 — Cấp 55 không đổi bậc trang phục (B3 từ 37) nhưng mở Kim Đan: người đang nhìn vẫn phải được báo. */
    @Test
    void shouldReportAVisibleChange_whenOnlyTheRealmChanges() {
        CharacterAppearance before = CharacterAppearance.of(Faction.FACTION_LOI, 54);

        assertThat(CharacterAppearance.of(Faction.FACTION_LOI, 55).differsVisiblyFrom(before))
                .isTrue();
        assertThat(CharacterAppearance.of(Faction.FACTION_LOI, 53).differsVisiblyFrom(before))
                .isFalse();
    }
}
