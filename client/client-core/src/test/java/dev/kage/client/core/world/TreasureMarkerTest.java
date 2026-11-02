package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.TreasureHuntResponse;

/** ADR-147 D8 — dấu X đi theo cuộc tìm server báo, không tự suy. */
class TreasureMarkerTest {

    private final TreasureMarker marker = new TreasureMarker();

    @Test
    void shouldShowAtTheSpotWhileHuntingAndHideOnceDug() {
        marker.apply(TreasureHuntResponse.newBuilder()
                .setHunting(true)
                .setSpotX(-35f)
                .build());
        assertThat(marker.isActive()).isTrue();
        assertThat(marker.x()).isEqualTo(-35f);

        marker.apply(TreasureHuntResponse.newBuilder().setHunting(false).build());
        assertThat(marker.isActive()).isFalse();
    }

    @Test
    void shouldIgnoreANonFiniteSpot() {
        marker.apply(TreasureHuntResponse.newBuilder()
                .setHunting(true)
                .setSpotX(Float.NaN)
                .build());
        assertThat(marker.isActive()).isFalse();
    }
}
