package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** ADR-077 — tâm pháp bị động cộng phẳng theo bậc, chỉ trên linh căn hiện tại. */
class MentalArtsTest {

    private static final EquipmentBonusSnapshot EQUIPMENT =
            new EquipmentBonusSnapshot(5, 4, 3, 20, EquipmentBonusSnapshot.Extras.speed(15));

    @Test
    void everyFactionShouldHaveExactlyTwoArts() {
        for (Element element : new Element[] {Element.HOA, Element.THUY, Element.PHONG, Element.THO, Element.LOI}) {
            assertThat(MentalArts.of(element)).as(element.name()).hasSize(2);
        }
        assertThat(MentalArts.of(Element.NONE)).isEmpty();
    }

    @Test
    void shouldAddPerRankBonusOnTopOfEquipment_andKeepMoveSpeed() {
        EquipmentBonusSnapshot result = MentalArts.applyTo(
                EQUIPMENT, Element.HOA, Map.of("hoa_liet_hoa_tam_kinh", 3, "hoa_hoa_linh_ho_the", 2));

        assertThat(result)
                .isEqualTo(new EquipmentBonusSnapshot(
                        5 + 2 * 3, 4, 3, 20 + 10 * 2, EquipmentBonusSnapshot.Extras.speed(15)));
    }

    @Test
    void shouldIgnoreArtsOfAnotherFaction_andClampOutOfRangeRanks() {
        assertThat(MentalArts.applyTo(EQUIPMENT, Element.THUY, Map.of("hoa_liet_hoa_tam_kinh", 5)))
                .isEqualTo(EQUIPMENT);
        assertThat(MentalArts.applyTo(EQUIPMENT, Element.LOI, Map.of("loi_loi_dinh_quyet", 99))
                        .atk())
                .isEqualTo(5 + 3 * MentalArts.MAX_RANK);
    }
}
