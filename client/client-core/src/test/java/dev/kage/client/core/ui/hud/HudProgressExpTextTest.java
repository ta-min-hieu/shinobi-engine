package dev.kage.client.core.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-366 — thanh EXP hiện thêm %. */
class HudProgressExpTextTest {

    @Test
    void appendsPercent() {
        assertThat(HudProgress.expText(250L, 1000L)).isEqualTo("250 / 1000 (25%)");
        assertThat(HudProgress.expText(0L, 1000L)).isEqualTo("0 / 1000 (0%)");
    }
}
