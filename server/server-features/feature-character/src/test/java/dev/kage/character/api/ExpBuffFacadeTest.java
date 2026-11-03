package dev.kage.character.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/** ADR-164 — x2 EXP chỉ trong hạn; hạn nhớ trong RAM (một lần đọc), {@code refresh} đọc lại. */
final class ExpBuffFacadeTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    @Test
    void shouldDoubleOnlyBeforeExpiry_andReadThePortOnce() {
        AtomicInteger reads = new AtomicInteger();
        ExpBuffFacade facade = new ExpBuffFacade(id -> {
            reads.incrementAndGet();
            return Optional.of(NOW.plusSeconds(60));
        });

        assertThat(facade.boosted(1L, 100L, NOW)).isEqualTo(200L);
        assertThat(facade.boosted(1L, 100L, NOW.plusSeconds(61))).isEqualTo(100L);
        assertThat(facade.secondsLeft(1L, NOW)).isEqualTo(60L);
        assertThat(reads.get()).isEqualTo(1);
    }

    @Test
    void refreshShouldPickUpANewExpiry() {
        AtomicReference<Optional<Instant>> stored = new AtomicReference<>(Optional.empty());
        ExpBuffFacade facade = new ExpBuffFacade(id -> stored.get());

        assertThat(facade.boosted(7L, 50L, NOW)).isEqualTo(50L);
        stored.set(Optional.of(NOW.plusSeconds(1800)));
        facade.refresh(7L);

        assertThat(facade.boosted(7L, 50L, NOW)).isEqualTo(100L);
    }
}
