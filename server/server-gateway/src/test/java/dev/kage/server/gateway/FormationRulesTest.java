package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.kage.character.domain.Faction;
import dev.kage.protocol.FormationChangedEvent;
import dev.kage.protocol.FormationTier;
import dev.kage.server.world.EquipmentBonusSnapshot;

/** ADR-128 — trận pháp: ≥2 người, mỗi linh căn tính một lần, bậc trận theo số linh căn khác nhau. */
class FormationRulesTest {

    @Test
    void shouldNotFormAlone() {
        assertThat(FormationRules.of(1, Set.of(Faction.HOA))).isEmpty();
    }

    @Test
    void shouldCountEachFactionOnceAndNotPunishSameFactionParties() {
        FormationRules.Formation fire =
                FormationRules.of(3, Set.of(Faction.HOA)).orElseThrow();

        assertThat(fire.tier()).isEqualTo(FormationRules.Tier.PLAIN);
        assertThat(fire.bonus()).isEqualTo(new EquipmentBonusSnapshot(FormationRules.MAJOR, 0, 0, 0));
    }

    @Test
    void shouldGrantThreeTalentsHpAtThreeFactions() {
        FormationRules.Formation formation = FormationRules.of(3, EnumSet.of(Faction.HOA, Faction.THUY, Faction.THO))
                .orElseThrow();

        assertThat(formation.tier()).isEqualTo(FormationRules.Tier.THREE_TALENTS);
        assertThat(formation.bonus())
                .isEqualTo(new EquipmentBonusSnapshot(
                        FormationRules.MAJOR,
                        FormationRules.MAJOR,
                        FormationRules.MAJOR,
                        FormationRules.THREE_TALENTS_HP));
    }

    @Test
    void shouldGrantFiveElementsWithEveryFaction() {
        FormationRules.Formation formation =
                FormationRules.of(5, EnumSet.allOf(Faction.class)).orElseThrow();

        assertThat(formation.tier()).isEqualTo(FormationRules.Tier.FIVE_ELEMENTS);
        assertThat(formation.bonus().atk())
                .isEqualTo(FormationRules.MAJOR + FormationRules.MINOR + FormationRules.LOI_ATK);
        assertThat(formation.bonus().hp()).isEqualTo(FormationRules.FIVE_ELEMENTS_HP);
    }

    @Test
    void shouldStackOntoTheBonusChainAndDescribeItForTheClient() {
        Optional<FormationRules.Formation> formation = FormationRules.of(2, EnumSet.of(Faction.PHONG, Faction.LOI));
        EquipmentBonusSnapshot base = new EquipmentBonusSnapshot(10, 10, 10, 100);

        assertThat(FormationRules.plus(base, formation))
                .isEqualTo(new EquipmentBonusSnapshot(
                        10 + FormationRules.MINOR + FormationRules.LOI_ATK,
                        10 + FormationRules.LOI_DEF,
                        10 + FormationRules.MINOR,
                        100));
        assertThat(FormationRules.plus(base, Optional.empty())).isSameAs(base);

        FormationChangedEvent event = FormationHooks.event(formation);
        assertThat(event.getMembers()).isEqualTo(2);
        assertThat(event.getTier()).isEqualTo(FormationTier.FORMATION_TIER_PLAIN);
        assertThat(event.getFactionsList())
                .containsExactly(dev.kage.protocol.Faction.FACTION_PHONG, dev.kage.protocol.Faction.FACTION_LOI);
        assertThat(FormationHooks.event(Optional.empty()).getMembers())
                .as("trận tan")
                .isZero();
    }
}
