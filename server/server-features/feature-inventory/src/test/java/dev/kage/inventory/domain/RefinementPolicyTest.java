package dev.kage.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

class RefinementPolicyTest {

    @Test
    void firstRefineShouldSeedOneToThreeLinesWithinBounds() {
        SplittableRandom random = new SplittableRandom(11);
        for (int i = 0; i < 500; i++) {
            List<RefinementPolicy.Line> lines =
                    RefinementPolicy.reroll(List.of(), RefinementPolicy.NO_LOCK, 30, random);
            assertThat(lines).hasSizeBetween(RefinementPolicy.MIN_LINES, RefinementPolicy.MAX_LINES);
            for (int index = 0; index < lines.size(); index++) {
                RefinementPolicy.Line line = lines.get(index);
                assertThat(line.index()).isEqualTo(index);
                assertThat(line.value())
                        .isBetween(RefinementPolicy.minValue(line.stat()), RefinementPolicy.maxValue(line.stat(), 30));
            }
        }
    }

    @Test
    void rerollShouldKeepCountAndLockedLine() {
        List<RefinementPolicy.Line> current = List.of(
                new RefinementPolicy.Line(0, RefinementPolicy.Stat.ATK, 2),
                new RefinementPolicy.Line(1, RefinementPolicy.Stat.HP, 9));
        List<RefinementPolicy.Line> next = RefinementPolicy.reroll(current, 1, 1, new SplittableRandom(5));

        assertThat(next).hasSize(2);
        assertThat(next.get(1)).isEqualTo(current.get(1));
    }

    @Test
    void shouldPriceLockAndSumFlat() {
        assertThat(RefinementPolicy.stoneCost(RefinementPolicy.NO_LOCK)).isEqualTo(1);
        assertThat(RefinementPolicy.stoneCost(0)).isEqualTo(3);
        assertThat(RefinementPolicy.maxValue(RefinementPolicy.Stat.DEF, 25)).isEqualTo(4);
        assertThat(RefinementPolicy.maxValue(RefinementPolicy.Stat.HP, 25)).isEqualTo(35);
        assertThat(RefinementPolicy.bonusOf(List.of(
                        new RefinementPolicy.Line(0, RefinementPolicy.Stat.ATK, 2),
                        new RefinementPolicy.Line(1, RefinementPolicy.Stat.ATK, 3),
                        new RefinementPolicy.Line(2, RefinementPolicy.Stat.HP, 12))))
                .isEqualTo(new EquipmentBonus(5, 0, 0, 12));
        assertThat(RefinementPolicy.refinable(EquipmentSlotType.PET)).isFalse();
        assertThat(RefinementPolicy.refinable(EquipmentSlotType.WEAPON)).isTrue();
    }
}
