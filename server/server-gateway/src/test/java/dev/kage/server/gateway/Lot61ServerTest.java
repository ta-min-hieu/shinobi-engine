package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import dev.kage.character.api.NewPlayersFacade;

/** Lô 61 — ngưỡng hộp thư (ADR-418), mốc đầu ngày giờ Việt Nam cho tân thủ (ADR-417). */
class Lot61ServerTest {

    @Test
    void warnsFromEightyPercent() {
        assertThat(Lot61Commands.nearlyFull(79, 100)).isFalse();
        assertThat(Lot61Commands.nearlyFull(80, 100)).isTrue();
        assertThat(Lot61Commands.nearlyFull(5, 0)).isFalse();
    }

    @Test
    void dayStartsAtVietnamMidnight() {
        // 2032-01-01T18:00Z = 01:00 ngày 02/01 giờ Việt Nam ⇒ đầu ngày là 2032-01-01T17:00Z.
        assertThat(NewPlayersFacade.startOfDay(Instant.parse("2032-01-01T18:00:00Z")))
                .isEqualTo(Instant.parse("2032-01-01T17:00:00Z"));
    }
}
