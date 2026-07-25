package dev.kage.trade.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TradeSessionTest {

    @Test
    void shouldRejectSameRequesterAndTarget() {
        assertThatThrownBy(() -> new TradeSession(1, 5, 5, TradeStatus.OPEN, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReportInvolvement() {
        TradeSession session = new TradeSession(1, 5, 7, TradeStatus.OPEN, Instant.now());

        assertThat(session.involves(5)).isTrue();
        assertThat(session.involves(7)).isTrue();
        assertThat(session.involves(999)).isFalse();
    }
}
