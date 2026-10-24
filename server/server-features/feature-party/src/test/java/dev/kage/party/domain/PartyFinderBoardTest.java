package dev.kage.party.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** ADR-082 — tin RAM, một tin mỗi trưởng nhóm, hết hạn 10 phút. */
class PartyFinderBoardTest {

    private static final Instant T0 = Instant.parse("2026-10-24T00:00:00Z");

    private static final class MutableClock extends Clock {
        Instant now = T0;

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void oneListingPerLeader_newestFirst_andClampedLevel() {
        MutableClock clock = new MutableClock();
        PartyFinderBoard board = new PartyFinderBoard(clock);

        board.post(1L, "A", PartyGoal.LEVELING, 0);
        clock.now = T0.plusSeconds(1);
        board.post(2L, "B", PartyGoal.DUNGEON, 500);
        board.post(1L, "A", PartyGoal.QUEST, 10);

        assertThat(board.active()).extracting(PartyFinderListing::leaderId).containsExactly(1L, 2L);
        assertThat(board.find(1L)).map(PartyFinderListing::goal).contains(PartyGoal.QUEST);
        assertThat(board.find(2L)).map(PartyFinderListing::minLevel).contains(PartyFinderBoard.MAX_LEVEL);
    }

    @Test
    void listingsShouldExpireAfterTenMinutes_andBeRemovable() {
        MutableClock clock = new MutableClock();
        PartyFinderBoard board = new PartyFinderBoard(clock);
        board.post(1L, "A", PartyGoal.LEVELING, 1);
        board.post(2L, "B", PartyGoal.LEVELING, 1);

        assertThat(board.remove(2L)).isTrue();
        assertThat(board.remove(2L)).isFalse();
        clock.now = T0.plus(PartyFinderBoard.LISTING_LIFETIME).plus(Duration.ofSeconds(1));
        assertThat(board.find(1L)).isEmpty();
        assertThat(board.active()).isEmpty();
    }
}
