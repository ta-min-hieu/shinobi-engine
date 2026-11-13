package dev.kage.client.core.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-332 — đồng hồ phiên chơi định dạng H:MM:SS không cấp phát chuỗi. */
class HudInfoLinesTest {

    @Test
    void formatsClock() {
        assertThat(clock(0)).isEqualTo("0:00:00");
        assertThat(clock(65)).isEqualTo("0:01:05");
        assertThat(clock(3_725)).isEqualTo("1:02:05");
        assertThat(clock(36_000)).isEqualTo("10:00:00");
    }

    private static String clock(long seconds) {
        StringBuilder out = new StringBuilder();
        HudInfoLines.appendClock(out, seconds);
        return out.toString();
    }
}
