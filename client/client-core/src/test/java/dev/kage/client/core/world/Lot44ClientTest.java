package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Lô 44 — khoá tên linh căn và cảnh giới khi đếm online (ADR-281). */
class Lot44ClientTest {

    private static final int REALMS = 6;

    @Test
    void mapsFactionNamesToKeys() {
        assertThat(OnlineCountCoordinator.factionKey("HOA")).isEqualTo("faction.hoa");
        assertThat(OnlineCountCoordinator.factionKey("NONE")).isEqualTo("faction.unspecified");
        assertThat(OnlineCountCoordinator.REALM_KEYS).hasSize(REALMS);
    }
}
