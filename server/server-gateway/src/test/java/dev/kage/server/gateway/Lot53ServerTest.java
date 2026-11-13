package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.character.api.PlayerLookupFacade;

/** Lô 53 — kiểm chuỗi tìm người chơi (ADR-353). */
class Lot53ServerTest {

    @Test
    void validatesSearchQuery() {
        assertThat(PlayerLookupFacade.validQuery("Ba")).isTrue();
        assertThat(PlayerLookupFacade.validQuery("B")).isFalse();
        assertThat(PlayerLookupFacade.validQuery("x".repeat(17))).isFalse();
        assertThat(PlayerLookupFacade.validQuery("a\u0000b")).isFalse();
        assertThat(PlayerLookupFacade.DISABLED.search("B")).isEmpty();
    }
}
