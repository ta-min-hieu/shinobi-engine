package dev.kage.dialogue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Value Object test — KHÔNG mock (feature-dialogue/CLAUDE.md). */
class NpcPlacementTest {

    @Test
    void shouldRejectBlankNpcCode() {
        assertThatThrownBy(() -> new NpcPlacement(" ", NpcPlacement.MAIN_WORLD_CODE, -5f, 0f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("npcCode");
    }

    @Test
    void shouldRejectNullNpcCode() {
        assertThatThrownBy(() -> new NpcPlacement(null, NpcPlacement.MAIN_WORLD_CODE, -5f, 0f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("npcCode");
    }

    @Test
    void shouldRejectBlankWorldCode() {
        assertThatThrownBy(() -> new NpcPlacement("town_elder", "", -5f, 0f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worldCode");
    }

    @Test
    void shouldAcceptNegativeX_becauseTheOnlyNpcInTheGameStandsAtMinusFive() {
        NpcPlacement elder = new NpcPlacement("town_elder", NpcPlacement.MAIN_WORLD_CODE, -5f, 0f);

        assertThat(elder.posX()).isEqualTo(-5f);
        assertThat(elder.posY()).isZero();
    }
}
