package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Lô 58 — ngưỡng vắng mặt của quà quay lại (ADR-394). */
class Lot58ServerTest {

    @Test
    void comebackNeedsSevenDaysAway() {
        Instant left = Instant.parse("2032-01-01T00:00:00Z");
        assertThat(Lot58Commands.deservesComeback(left, left.plus(Duration.ofDays(7))))
                .isTrue();
        assertThat(Lot58Commands.deservesComeback(
                        left, left.plus(Duration.ofDays(7)).minusSeconds(1)))
                .isFalse();
        assertThat(Lot58Commands.deservesComeback(left, left.plus(Duration.ofDays(30))))
                .isTrue();
    }
}
