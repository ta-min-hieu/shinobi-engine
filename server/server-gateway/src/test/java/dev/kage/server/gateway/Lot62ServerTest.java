package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Lô 62 — làm sạch câu chào khi vào game (ADR-425). */
class Lot62ServerTest {

    @Test
    void cleansGreeting() {
        assertThat(Lot62Commands.cleanGreeting("  chào \n chư vị ")).contains("chào   chư vị");
        assertThat(Lot62Commands.cleanGreeting("")).contains("");
        assertThat(Lot62Commands.cleanGreeting("x".repeat(61))).isEmpty();
        assertThat(Lot62Commands.cleanGreeting("a\u0007b"))
                .hasValueSatisfying(text -> assertThat(text).doesNotContain("\u0007"));
    }
}
