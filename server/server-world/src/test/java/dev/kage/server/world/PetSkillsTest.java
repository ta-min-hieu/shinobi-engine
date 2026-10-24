package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import dev.kage.server.world.component.CombatStatsComponent;

/** ADR-086 — linh thú đánh thêm 50% / chắn nửa sát thương theo xác suất, chỉ trên đòn trúng. */
class PetSkillsTest {

    /** Random luôn trả 0 ⇒ mọi xác suất > 0 đều kích hoạt. */
    private static final RandomGenerator ALWAYS = () -> 0L;

    @Test
    void strikeShouldAddHalf_guardShouldHalve_andMissesStayMisses() {
        CombatStatsComponent fox = new CombatStatsComponent();
        fox.petStrikePermille = 100;
        CombatStatsComponent turtle = new CombatStatsComponent();
        turtle.petGuardPermille = 100;
        CombatStatsComponent none = new CombatStatsComponent();
        CombatConstants.AttackResult hit = new CombatConstants.AttackResult(false, false, 20);

        assertThat(PetSkills.apply(hit, fox, none, ALWAYS).damage()).isEqualTo(30);
        assertThat(PetSkills.apply(hit, none, turtle, ALWAYS).damage()).isEqualTo(10);
        assertThat(PetSkills.apply(hit, fox, turtle, ALWAYS).damage()).isEqualTo(15);
        assertThat(PetSkills.apply(hit, none, none, ALWAYS)).isSameAs(hit);
        CombatConstants.AttackResult miss = new CombatConstants.AttackResult(true, false, 0);
        assertThat(PetSkills.apply(miss, fox, turtle, ALWAYS)).isSameAs(miss);
    }
}
