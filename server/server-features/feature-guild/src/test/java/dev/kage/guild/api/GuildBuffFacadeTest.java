package dev.kage.guild.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.kage.guild.domain.GuildBuffPort;
import dev.kage.guild.domain.GuildLevelTable;

/** ADR-239 — +% khi còn hạn, đọc qua bộ đệm, bật xong xoá đệm. */
class GuildBuffFacadeTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    /** Port giả: một bang, đếm số lần đọc hạn. */
    private static final class FakePort implements GuildBuffPort {
        private Instant until = Instant.EPOCH;
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public Activation activate(long leaderId, long cost, Instant newUntil, Instant now) {
            until = newUntil;
            return new Activation(Outcome.OK, newUntil, 0L, 1L);
        }

        @Override
        public Optional<Instant> buffUntilOf(long characterId) {
            reads.incrementAndGet();
            return Optional.of(until);
        }
    }

    @Test
    void boostsOnlyWhileActiveAndCachesReads() {
        FakePort port = new FakePort();
        GuildBuffFacade facade = new GuildBuffFacade(port);

        assertThat(facade.boosted(5L, 100L, NOW)).isEqualTo(100L);
        assertThat(facade.boosted(5L, 100L, NOW.plusSeconds(5))).isEqualTo(100L);
        assertThat(port.reads.get()).isEqualTo(1);

        facade.activate(1L, NOW);
        assertThat(facade.boosted(5L, 100L, NOW.plusSeconds(6))).isEqualTo(120L);
        assertThat(facade.boosted(5L, 100L, NOW.plus(GuildBuffFacade.DURATION).plusSeconds(60)))
                .isEqualTo(100L);
        assertThat(facade.secondsLeft(5L, NOW)).isEqualTo(GuildBuffFacade.DURATION.toSeconds());
    }

    @Test
    void levelFloorMatchesTable() {
        assertThat(GuildLevelTable.expToReach(1)).isZero();
        assertThat(GuildLevelTable.expToReach(2)).isEqualTo(10_000L);
        assertThat(GuildLevelTable.levelFor(GuildLevelTable.expToReach(3))).isEqualTo(3);
    }
}
