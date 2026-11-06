package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.ItemUseEffect;

/** ADR-176 — trần số lần và loại món dùng nhiều được. */
final class BulkUseCommandsTest {

    @Test
    void countIsClampedOnTheServer() {
        assertThat(BulkUseCommands.clamp(0)).isEqualTo(1);
        assertThat(BulkUseCommands.clamp(-5)).isEqualTo(1);
        assertThat(BulkUseCommands.clamp(7)).isEqualTo(7);
        assertThat(BulkUseCommands.clamp(1_000)).isEqualTo(BulkUseCommands.MAX_COUNT);
    }

    @Test
    void healingAndPetEvolutionAreNeverBulkUsable() {
        assertThat(BulkUseCommands.BULK_EFFECTS)
                .doesNotContain(ItemUseEffect.HEAL_HP, ItemUseEffect.EVOLVE_PET)
                .contains(ItemUseEffect.DOUBLE_EXP, ItemUseEffect.EXPAND_BAG);
    }
}
