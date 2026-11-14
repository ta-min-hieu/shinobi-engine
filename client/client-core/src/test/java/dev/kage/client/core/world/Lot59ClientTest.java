package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.hud.ClockFormat;

/** Lô 59 phía client — mức FPS (ADR-403), giờ 12h (ADR-406), đồng hồ giao chiến (ADR-405). */
class Lot59ClientTest {

    @Test
    void acceptsOnlyListedFpsCaps() {
        assertThat(ClientToolsCoordinator.parseCap("60")).contains(60);
        assertThat(ClientToolsCoordinator.parseCap("0")).contains(0);
        assertThat(ClientToolsCoordinator.parseCap("75")).isEmpty();
        assertThat(ClientToolsCoordinator.parseCap("abc")).isEmpty();
    }

    @Test
    void formatsTwelveHourClock() {
        assertThat(format(0, true)).isEqualTo("12:00 AM");
        assertThat(format(9 * 60 + 5, true)).isEqualTo("9:05 AM");
        assertThat(format(12 * 60, true)).isEqualTo("12:00 PM");
        assertThat(format(23 * 60 + 59, true)).isEqualTo("11:59 PM");
        assertThat(format(9 * 60 + 5, false)).isEqualTo("09:05");
    }

    private static String format(int minuteOfDay, boolean twelve) {
        StringBuilder out = new StringBuilder();
        ClockFormat.appendHourMinute(out, minuteOfDay, twelve);
        return out.toString();
    }
}
