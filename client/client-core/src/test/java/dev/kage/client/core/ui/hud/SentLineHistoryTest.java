package dev.kage.client.core.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-262 — ↑/↓ đi qua các dòng đã gửi. */
class SentLineHistoryTest {

    @Test
    void walksBackAndForward() {
        SentLineHistory history = new SentLineHistory();
        assertThat(history.previous()).isEmpty();
        history.remember("chào");
        history.remember("  ");
        history.remember("/den chu hoanh");
        history.remember("/den chu hoanh");
        assertThat(history.previous()).contains("/den chu hoanh");
        assertThat(history.previous()).contains("chào");
        assertThat(history.previous()).contains("chào");
        assertThat(history.next()).contains("/den chu hoanh");
        assertThat(history.next()).contains("");
        history.remember("mới");
        assertThat(history.previous()).contains("mới");
    }

    @Test
    void keepsAtMostThirtyLines() {
        SentLineHistory history = new SentLineHistory();
        for (int i = 0; i < 40; i++) {
            history.remember("dòng " + i);
        }
        String oldest = "";
        for (int i = 0; i < 50; i++) {
            oldest = history.previous().orElseThrow();
        }
        assertThat(oldest).isEqualTo("dòng 10");
    }
}
