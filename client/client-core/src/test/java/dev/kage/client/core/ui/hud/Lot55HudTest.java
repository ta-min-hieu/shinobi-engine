package dev.kage.client.core.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Lô 55 — giờ máy (ADR-372), màu đồ theo cường hoá (ADR-373). */
class Lot55HudTest {

    @Test
    void formatsHourMinute() {
        StringBuilder out = new StringBuilder();
        HudInfoLines.appendHourMinute(out, 9 * 60 + 5);
        assertThat(out).hasToString("09:05");
    }

    @Test
    void readsEnhanceLevel() {
        assertThat(InventoryPanel.enhanceLevelOf("Trường kiếm +12")).isEqualTo(12);
        assertThat(InventoryPanel.enhanceLevelOf("Kunai x20")).isZero();
        assertThat(InventoryPanel.enhanceLevelOf("Áo +")).isZero();
        assertThat(InventoryPanel.enhanceColor("Áo +3")).isNotEqualTo(InventoryPanel.enhanceColor("Áo"));
        assertThat(InventoryPanel.enhanceColor("Áo +7")).isNotEqualTo(InventoryPanel.enhanceColor("Áo +3"));
        assertThat(InventoryPanel.enhanceColor("Áo +10")).isNotEqualTo(InventoryPanel.enhanceColor("Áo +7"));
    }
}
