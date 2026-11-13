package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.client.core.input.KeyBindings;

/** Lô 50 phía client — chữ +EXP (ADR-331), mục tiêu cấp (ADR-334), phím ẩn chat (ADR-333). */
class Lot50ClientTest {

    @Test
    void expGainIgnoresFirstSnapshotAndDecreases() {
        assertThat(ExpGainCoordinator.gainOf(-1L, 500L)).isZero();
        assertThat(ExpGainCoordinator.gainOf(500L, 540L)).isEqualTo(40L);
        assertThat(ExpGainCoordinator.gainOf(540L, 540L)).isZero();
        assertThat(ExpGainCoordinator.gainOf(540L, 100L)).isZero();
    }

    @Test
    void goalPercentClamps() {
        assertThat(LevelGoalCoordinator.percentOf(45L, 100L)).isEqualTo(45);
        assertThat(LevelGoalCoordinator.percentOf(0L, 100L)).isZero();
        assertThat(LevelGoalCoordinator.percentOf(150L, 100L)).isEqualTo(100);
        assertThat(LevelGoalCoordinator.percentOf(5L, 0L)).isEqualTo(100);
    }

    @Test
    void hideChatHasItsOwnDefaultKey() {
        KeyBindings bindings = KeyBindings.load(new KeyBindings.MemoryStore());
        assertThat(KeyBindings.Binding.byId("anchat")).contains(KeyBindings.Binding.HIDE_CHAT);
        int key = bindings.keyOf(KeyBindings.Binding.HIDE_CHAT);
        for (KeyBindings.Binding other : KeyBindings.Binding.values()) {
            if (other != KeyBindings.Binding.HIDE_CHAT) {
                assertThat(bindings.keyOf(other)).isNotEqualTo(key);
            }
        }
    }
}
