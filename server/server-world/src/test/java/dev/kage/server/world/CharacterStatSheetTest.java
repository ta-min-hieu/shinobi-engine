package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Bảng nhân vật + Lực Chiến (ADR-056): cùng công thức trận đánh dùng, cộng trang bị đúng như
 * {@code applyBonusTo}, và Lực Chiến đúng công thức GDD §5.
 */
class CharacterStatSheetTest {

    private static final CharacterBaseStats BASE = new CharacterBaseStats(10, 20, 8, 12, Element.NONE, 5);

    @Test
    void shouldAddEquipmentBonusOnTopOfTheDerivedStats() {
        CharacterStatSheet naked = WorldFacade.statSheet(BASE, EquipmentBonusSnapshot.NONE);
        CharacterStatSheet geared = WorldFacade.statSheet(BASE, new EquipmentBonusSnapshot(7, 3, 2, 18));

        assertThat(geared.attack()).isEqualTo(naked.attack() + 7);
        assertThat(geared.defense()).isEqualTo(naked.defense() + 3);
        assertThat(geared.resistance()).isEqualTo(naked.resistance() + 2);
        assertThat(geared.maxHp()).isEqualTo(naked.maxHp() + 18);
        assertThat(geared.equipmentBonusHp()).isEqualTo(18);
        assertThat(geared.combatPower()).isGreaterThan(naked.combatPower());
    }

    @Test
    void shouldFollowTheGddCombatPowerFormula() {
        CharacterStatSheet sheet = WorldFacade.statSheet(BASE, EquipmentBonusSnapshot.NONE);

        // GDD §5 — tính tay từ chính các số trên bảng (tỉ lệ ‰ ⇒ điểm phần trăm = ‰ / 10).
        double expected = sheet.maxHp()
                + sheet.maxChi() * 0.5
                + (sheet.attack() + sheet.skillDamage()) * 3.5
                + (sheet.defense() + sheet.resistance()) * 3.0
                + sheet.critPermille() / 10.0 * 2.0
                + sheet.dodgePermille() / 10.0 * 1.5
                + sheet.hitPermille() / 10.0 * 0.5
                + sheet.level() * 50.0;
        assertThat((double) sheet.combatPower()).isCloseTo(expected, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void shouldReportTheSameMaxHpAsTheWorldUsesOnAttach() {
        EquipmentBonusSnapshot bonus = new EquipmentBonusSnapshot(0, 0, 0, 25);

        assertThat(WorldFacade.statSheet(BASE, bonus).maxHp()).isEqualTo(WorldFacade.characterMaxHp(5, bonus));
    }
}
