package dev.kage.pvp.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.kage.pvp.domain.TournamentPort;
import dev.kage.pvp.domain.TournamentRules;

/** ADR-125 — luận võ: đăng ký trong ngày, loại trực tiếp qua đấu trường, vắng mặt xử thua, một người thì vô địch. */
class TournamentDirectorTest {

    /** Chủ nhật 01/11/2026 — 10:00 và 20:00 giờ VN. */
    private static final Instant MORNING = Instant.parse("2026-11-01T03:00:00Z");

    private static final Instant START = Instant.parse("2026-11-01T13:00:00Z");
    private static final TournamentRules.Schedule SCHEDULE =
            new TournamentRules.Schedule(DayOfWeek.SUNDAY, LocalTime.of(20, 0));

    /** Port trong bộ nhớ. */
    private static final class FakePort implements TournamentPort {
        Tournament tournament;
        final List<Long> entrants = new ArrayList<>();
        final Set<Long> eliminated = new HashSet<>();
        final Map<Long, Match> matches = new LinkedHashMap<>();
        long nextMatchId = 1L;

        @Override
        public Tournament ensure(Instant startAt) {
            if (tournament == null) {
                tournament = new Tournament(1L, startAt, Status.OPEN, 0, 0L);
            }
            return tournament;
        }

        @Override
        public RegisterOutcome register(long tournamentId, long characterId, int maxEntrants) {
            if (entrants.contains(characterId)) {
                return RegisterOutcome.ALREADY_REGISTERED;
            }
            entrants.add(characterId);
            return RegisterOutcome.OK;
        }

        @Override
        public List<Long> alive(long tournamentId) {
            return entrants.stream().filter(id -> !eliminated.contains(id)).toList();
        }

        @Override
        public void update(long tournamentId, Status status, int round, long championId) {
            tournament = new Tournament(1L, tournament.startAt(), status, round, championId);
        }

        @Override
        public List<Match> matches(long tournamentId, int round) {
            return matches.values().stream().filter(m -> m.round() == round).toList();
        }

        @Override
        public void createMatch(long tournamentId, int round, long aId, long bId) {
            long id = nextMatchId++;
            matches.put(id, new Match(id, round, aId, bId, bId == 0L ? aId : 0L, Optional.empty()));
        }

        @Override
        public void markStarted(long matchId, Instant startedAt) {
            Match m = matches.get(matchId);
            matches.put(matchId, new Match(matchId, m.round(), m.aId(), m.bId(), 0L, Optional.of(startedAt)));
        }

        @Override
        public void finishMatch(long matchId, long winnerId, long loserId) {
            Match m = matches.get(matchId);
            if (m.winnerId() != 0L) {
                return;
            }
            matches.put(matchId, new Match(matchId, m.round(), m.aId(), m.bId(), winnerId, m.startedAt()));
            eliminated.add(loserId);
        }

        @Override
        public Optional<Match> openMatchBetween(long firstId, long secondId) {
            return matches.values().stream()
                    .filter(m -> m.winnerId() == 0L)
                    .filter(m ->
                            (m.aId() == firstId && m.bId() == secondId) || (m.aId() == secondId && m.bId() == firstId))
                    .findFirst();
        }
    }

    /** Đấu trường giả: ai online, các trận đã mở. */
    private static final class FakeArena implements TournamentDirector.Arena, TournamentDirector.Announcer {
        final Set<Long> online = new HashSet<>();
        final List<long[]> duels = new ArrayList<>();
        final List<Long> forfeits = new ArrayList<>();
        long champion;

        @Override
        public boolean online(long characterId) {
            return online.contains(characterId);
        }

        @Override
        public boolean startDuel(long aId, long bId) {
            duels.add(new long[] {aId, bId});
            return true;
        }

        @Override
        public void forfeit(long characterId) {
            forfeits.add(characterId);
        }

        @Override
        public void matchAssigned(long characterId, long opponentId, int round) {}

        @Override
        public void champion(long characterId) {
            champion = characterId;
        }
    }

    @Test
    void registrationShouldOnlyOpenOnTheDayBeforeTheStartAndNeedLevelTen() {
        TournamentDirector director = new TournamentDirector(new FakePort(), SCHEDULE, 1L);

        assertThat(director.register(1L, 30, MORNING.minusSeconds(86_400)))
                .isEqualTo(TournamentPort.RegisterOutcome.CLOSED);
        assertThat(director.register(1L, 9, MORNING)).isEqualTo(TournamentPort.RegisterOutcome.LEVEL_TOO_LOW);
        assertThat(director.register(1L, 30, MORNING)).isEqualTo(TournamentPort.RegisterOutcome.OK);
        assertThat(director.register(1L, 30, MORNING)).isEqualTo(TournamentPort.RegisterOutcome.ALREADY_REGISTERED);
        assertThat(director.register(2L, 30, START)).isEqualTo(TournamentPort.RegisterOutcome.CLOSED);
    }

    @Test
    void threeEntrantsShouldPlayTwoRoundsAndCrownOneChampion() {
        FakePort port = new FakePort();
        FakeArena arena = new FakeArena();
        TournamentDirector director = new TournamentDirector(port, SCHEDULE, 7L);
        for (long id = 1L; id <= 3L; id++) {
            director.register(id, 30, MORNING);
            arena.online.add(id);
        }

        director.tick(START.minusSeconds(1), arena, arena);
        assertThat(port.tournament.status()).as("chưa tới giờ").isEqualTo(TournamentPort.Status.OPEN);

        director.tick(START, arena, arena); // ghép vòng 1: một trận + một người đi thẳng
        assertThat(port.tournament.round()).isEqualTo(1);
        assertThat(port.matches(1L, 1)).hasSize(2);
        director.tick(START.plusSeconds(15), arena, arena); // mở trận
        assertThat(arena.duels).hasSize(1);
        long[] first = arena.duels.getFirst();
        director.onDuelSettled(first[0], first[1]);

        director.tick(START.plusSeconds(30), arena, arena); // vòng 1 xong ⇒ ghép vòng 2
        assertThat(port.tournament.round()).isEqualTo(2);
        director.tick(START.plusSeconds(45), arena, arena);
        long[] finalDuel = arena.duels.getLast();
        director.onDuelSettled(finalDuel[1], finalDuel[0]);

        director.tick(START.plusSeconds(60), arena, arena);
        assertThat(port.tournament.status()).isEqualTo(TournamentPort.Status.FINISHED);
        assertThat(arena.champion).isEqualTo(finalDuel[1]);
        assertThat(port.tournament.championId()).isEqualTo(finalDuel[1]);
    }

    @Test
    void absentPlayersShouldLoseAndOverlongDuelsShouldBeForfeitedByTheChallenger() {
        FakePort port = new FakePort();
        FakeArena arena = new FakeArena();
        TournamentDirector director = new TournamentDirector(port, SCHEDULE, 3L);
        for (long id = 1L; id <= 4L; id++) {
            director.register(id, 30, MORNING);
        }
        arena.online.addAll(List.of(1L, 2L, 3L)); // 4 vắng mặt

        director.tick(START, arena, arena);
        director.tick(START.plusSeconds(15), arena, arena);
        assertThat(port.eliminated).as("người vắng mặt bị xử thua").contains(4L);

        TournamentPort.Match live = port.matches(1L, 1).stream()
                .filter(m -> m.startedAt().isPresent())
                .findFirst()
                .orElseThrow();
        director.tick(START.plus(TournamentRules.MATCH_TIMEOUT).plusSeconds(30), arena, arena);
        assertThat(arena.forfeits).containsExactly(live.aId());
    }
}
