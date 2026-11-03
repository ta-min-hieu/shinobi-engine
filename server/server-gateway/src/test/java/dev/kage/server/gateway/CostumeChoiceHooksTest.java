package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.Faction;

/** ADR-166 — bậc hiện = min(bậc chọn, bậc đã mở); chưa chọn hay chưa biết ngoại hình thì giữ nguyên. */
final class CostumeChoiceHooksTest {

    private static final int LEVEL_B3 = 40;

    private final CostumeChoiceHooks hooks = new CostumeChoiceHooks();

    @Test
    void chosenLowerTierShouldBeShown() {
        hooks.bind(characterId -> 1);

        CharacterAppearance shown = hooks.apply(7L, CharacterAppearance.of(Faction.FACTION_HOA, LEVEL_B3));

        assertThat(shown.costumeTier()).isEqualTo(CostumeTier.COSTUME_TIER_B1);
        assertThat(shown.level()).isEqualTo(LEVEL_B3);
    }

    @Test
    void chosenTierAboveTheUnlockedOneShouldNeverShow() {
        hooks.bind(characterId -> 3);

        CharacterAppearance shown = hooks.apply(7L, CharacterAppearance.of(Faction.FACTION_HOA, 1));

        assertThat(shown.costumeTier()).isEqualTo(CostumeTier.COSTUME_TIER_B1);
    }

    @Test
    void noChoiceOrUnknownAppearanceShouldPassThrough() {
        CharacterAppearance natural = CharacterAppearance.of(Faction.FACTION_THUY, LEVEL_B3);
        assertThat(hooks.apply(7L, natural)).isEqualTo(natural);

        hooks.bind(characterId -> 1);
        assertThat(hooks.apply(7L, CharacterAppearance.unknown())).isEqualTo(CharacterAppearance.unknown());
    }
}
