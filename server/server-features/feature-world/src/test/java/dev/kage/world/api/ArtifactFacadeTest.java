package dev.kage.world.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.world.domain.ArtifactPort;

/** ADR-127 — hồi chiêu pháp bảo do server giữ; món không có chiêu thì từ chối. */
class ArtifactFacadeTest {

    private static final long BELL = 7L;
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    private static ArtifactFacade facade() {
        return new ArtifactFacade(() -> Map.of(BELL, new ArtifactPort.ArtifactSkill(BELL, "SHIELD", 25, 10, 60)));
    }

    @Test
    void shouldUseThenCoolDownThenBeReadyAgain() {
        ArtifactFacade artifacts = facade();

        assertThat(artifacts.use(1L, Optional.of(BELL), NOW)).isInstanceOf(ArtifactFacade.Use.Ready.class);
        assertThat(artifacts.use(1L, Optional.of(BELL), NOW.plusSeconds(20)))
                .isEqualTo(new ArtifactFacade.Use.Cooling(40L));
        assertThat(artifacts.use(2L, Optional.of(BELL), NOW.plusSeconds(20)))
                .as("hồi chiêu theo từng nhân vật")
                .isInstanceOf(ArtifactFacade.Use.Ready.class);
        assertThat(artifacts.use(1L, Optional.of(BELL), NOW.plusSeconds(60)))
                .isInstanceOf(ArtifactFacade.Use.Ready.class);
    }

    @Test
    void shouldRefuseWhenNothingOrANonArtifactIsWorn() {
        ArtifactFacade artifacts = facade();

        assertThat(artifacts.use(1L, Optional.empty(), NOW)).isInstanceOf(ArtifactFacade.Use.NoSkill.class);
        assertThat(artifacts.use(1L, Optional.of(99L), NOW)).isInstanceOf(ArtifactFacade.Use.NoSkill.class);
    }
}
