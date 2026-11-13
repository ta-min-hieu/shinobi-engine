package dev.kage.client.core.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-355 — khung mục tiêu hiện thêm % máu. */
class HudTargetHpTextTest {

    @Test
    void appendsPercent() {
        assertThat(HudTargetFrame.hpText(45, 90)).isEqualTo("45 / 90 (50%)");
        assertThat(HudTargetFrame.hpText(0, 90)).isEqualTo("0 / 90 (0%)");
        assertThat(HudTargetFrame.hpText(120, 100)).isEqualTo("120 / 100 (100%)");
    }
}
