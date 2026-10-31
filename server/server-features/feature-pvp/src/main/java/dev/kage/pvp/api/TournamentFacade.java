package dev.kage.pvp.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.kage.pvp.application.TournamentDirector;
import dev.kage.pvp.domain.TournamentPort;
import dev.kage.pvp.domain.TournamentRules;

/** Luận võ đại hội (ADR-125). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class TournamentFacade {

    /** Composition không có DB (test) — không giải nào. */
    public static final TournamentFacade DISABLED =
            new TournamentFacade(new TournamentDirector(new NoTournaments(), TournamentRules.DEFAULT_SCHEDULE, 0L));

    private final TournamentDirector director;

    /** Dựng ở composition root. */
    public TournamentFacade(TournamentDirector director) {
        this.director = director;
    }

    /** Đăng ký giải hôm nay — {@code level} đọc từ DB ở chỗ gọi. */
    public TournamentPort.RegisterOutcome register(long characterId, int level, Instant now) {
        return director.register(characterId, level, now);
    }

    /** Giải tuần này. */
    public TournamentPort.Tournament current(Instant now) {
        return director.current(now);
    }

    /** Một trận đấu trường vừa chốt. */
    public void onDuelSettled(long winnerId, long loserId) {
        director.onDuelSettled(winnerId, loserId);
    }

    /** Một nhịp điều khiển giải. */
    public void tick(Instant now, TournamentDirector.Arena arena, TournamentDirector.Announcer announcer) {
        director.tick(now, arena, announcer);
    }

    /** Port rỗng cho {@link #DISABLED}. */
    private static final class NoTournaments implements TournamentPort {
        @Override
        public Tournament ensure(Instant startAt) {
            return new Tournament(0L, startAt, Status.FINISHED, 0, 0L);
        }

        @Override
        public RegisterOutcome register(long tournamentId, long characterId, int maxEntrants) {
            return RegisterOutcome.CLOSED;
        }

        @Override
        public List<Long> alive(long tournamentId) {
            return List.of();
        }

        @Override
        public void update(long tournamentId, Status status, int round, long championId) {
            // tắt
        }

        @Override
        public List<Match> matches(long tournamentId, int round) {
            return List.of();
        }

        @Override
        public void createMatch(long tournamentId, int round, long aId, long bId) {
            // tắt
        }

        @Override
        public void markStarted(long matchId, Instant startedAt) {
            // tắt
        }

        @Override
        public void finishMatch(long matchId, long winnerId, long loserId) {
            // tắt
        }

        @Override
        public Optional<Match> openMatchBetween(long firstId, long secondId) {
            return Optional.empty();
        }
    }
}
