package dev.kage.guild.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class GuildTest {

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> new Guild(1, "  ", 5, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
