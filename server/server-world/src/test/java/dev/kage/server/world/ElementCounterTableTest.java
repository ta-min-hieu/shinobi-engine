package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** GDD §4 chu trình khắc: Hỏa→Phong→Thổ→Thủy→Hỏa; Lôi/NONE trung lập tuyệt đối. */
class ElementCounterTableTest {

    @Test
    void shouldReturnAdvantageMultiplier_forEachStepOfCounterCycle() {
        assertThat(ElementCounterTable.multiplier(Element.HOA, Element.PHONG)).isEqualTo(1.15f);
        assertThat(ElementCounterTable.multiplier(Element.PHONG, Element.THO)).isEqualTo(1.15f);
        assertThat(ElementCounterTable.multiplier(Element.THO, Element.THUY)).isEqualTo(1.15f);
        assertThat(ElementCounterTable.multiplier(Element.THUY, Element.HOA)).isEqualTo(1.15f);
    }

    @Test
    void shouldReturnDisadvantageMultiplier_whenDefenderCountersAttacker() {
        assertThat(ElementCounterTable.multiplier(Element.PHONG, Element.HOA)).isEqualTo(0.85f);
        assertThat(ElementCounterTable.multiplier(Element.THO, Element.PHONG)).isEqualTo(0.85f);
        assertThat(ElementCounterTable.multiplier(Element.THUY, Element.THO)).isEqualTo(0.85f);
        assertThat(ElementCounterTable.multiplier(Element.HOA, Element.THUY)).isEqualTo(0.85f);
    }

    @Test
    void shouldReturnNeutralMultiplier_whenNoCounterRelation() {
        assertThat(ElementCounterTable.multiplier(Element.HOA, Element.THO)).isEqualTo(1.0f);
        assertThat(ElementCounterTable.multiplier(Element.HOA, Element.HOA)).isEqualTo(1.0f);
    }

    @Test
    void shouldReturnNeutralMultiplier_whenEitherSideIsLoiOrNone() {
        assertThat(ElementCounterTable.multiplier(Element.LOI, Element.HOA)).isEqualTo(1.0f);
        assertThat(ElementCounterTable.multiplier(Element.HOA, Element.LOI)).isEqualTo(1.0f);
        assertThat(ElementCounterTable.multiplier(Element.NONE, Element.PHONG)).isEqualTo(1.0f);
        assertThat(ElementCounterTable.multiplier(Element.HOA, Element.NONE)).isEqualTo(1.0f);
    }
}
