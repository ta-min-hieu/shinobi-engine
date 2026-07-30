package dev.kage.pvp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class PvpRatingTest {

    @Test
    void shouldRejectNegativeRating() {
        assertThatThrownBy(() -> new PvpRating(1L, -1, 0, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeWinsOrLosses() {
        assertThatThrownBy(() -> new PvpRating(1L, 1000, -1, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PvpRating(1L, 1000, 0, -1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldConstructSuccessfully_withValidValues() {
        PvpRating rating = new PvpRating(1L, 1016, 1, 0, Instant.now());

        assertThat(rating.rating()).isEqualTo(1016);
        assertThat(rating.wins()).isEqualTo(1);
    }
}
