package dev.kage.pvp.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

import dev.kage.pvp.domain.TournamentPort;
import dev.kage.pvp.domain.TournamentRules;

/**
 * Điều khiển luận võ đại hội (ADR-125) — gọi {@link #tick} định kỳ (Virtual Thread). Mọi trạng thái nằm ở DB qua
 * {@link TournamentPort}; vòng đời trận đấu ở {@link Arena} (gateway, World đấu trường ADR-051). Một lượt: tới giờ thì
 * bắt đầu (ghép vòng 1), trong lúc chạy thì mở các trận chưa mở (ai vắng mặt thì xử thua), xử thua người thách nếu trận
 * quá giờ, hết trận của vòng thì ghép vòng sau, còn một người thì trao chức vô địch.
 */
public final class TournamentDirector {

    /** Phía World đấu trường — gateway hiện thực. */
    public interface Arena {

        boolean online(long characterId);

        /** Mở trận đấu trường giữa hai người; {@code false} nếu một trong hai đang bận. */
        boolean startDuel(long aId, long bId);

        /** Xử thua {@code characterId} trong trận đang đấu (nếu có). */
        void forfeit(long characterId);
    }

    /** Phía thưởng + thông báo — gateway hiện thực. */
    public interface Announcer {

        /** Vòng mới bắt đầu: báo từng cặp. {@code opponentId == 0} = được đi thẳng. */
        void matchAssigned(long characterId, long opponentId, int round);

        /** Trao thưởng vô địch + báo cả server. */
        void champion(long characterId);
    }

    private final TournamentPort port;
    private final TournamentRules.Schedule schedule;
    private final SplittableRandom random;

    public TournamentDirector(TournamentPort port, TournamentRules.Schedule schedule, long seed) {
        this.port = port;
        this.schedule = schedule;
        this.random = new SplittableRandom(seed);
    }

    /** Đăng ký cho giải hôm nay (chỉ trong giờ đăng ký). */
    public TournamentPort.RegisterOutcome register(long characterId, int level, Instant now) {
        if (!TournamentRules.registrationOpen(now, schedule)) {
            return TournamentPort.RegisterOutcome.CLOSED;
        }
        if (level < TournamentRules.MIN_LEVEL) {
            return TournamentPort.RegisterOutcome.LEVEL_TOO_LOW;
        }
        TournamentPort.Tournament tournament = port.ensure(TournamentRules.startOfWeek(now, schedule));
        return port.register(tournament.id(), characterId, TournamentRules.MAX_ENTRANTS);
    }

    /** Giải tuần này (để hiển thị). */
    public TournamentPort.Tournament current(Instant now) {
        return port.ensure(TournamentRules.startOfWeek(now, schedule));
    }

    /** Một trận đấu trường vừa chốt — nếu là trận của giải thì ghi kết quả. */
    public void onDuelSettled(long winnerId, long loserId) {
        port.openMatchBetween(winnerId, loserId).ifPresent(match -> port.finishMatch(match.id(), winnerId, loserId));
    }

    public void tick(Instant now, Arena arena, Announcer announcer) {
        Optional<Instant> start = TournamentRules.todaysStart(now, schedule);
        if (start.isEmpty() || now.isBefore(start.get())) {
            return;
        }
        TournamentPort.Tournament tournament = port.ensure(start.get());
        if (tournament.status() == TournamentPort.Status.OPEN) {
            nextRound(tournament, 1, announcer);
        } else if (tournament.status() == TournamentPort.Status.RUNNING) {
            advance(tournament, now, arena, announcer);
        }
    }

    private void advance(TournamentPort.Tournament tournament, Instant now, Arena arena, Announcer announcer) {
        for (TournamentPort.Match match : port.matches(tournament.id(), tournament.round())) {
            if (match.winnerId() == 0L) {
                drive(match, now, arena);
            }
        }
        boolean roundDone =
                port.matches(tournament.id(), tournament.round()).stream().allMatch(match -> match.winnerId() != 0L);
        if (roundDone) {
            nextRound(tournament, tournament.round() + 1, announcer);
        }
    }

    private void drive(TournamentPort.Match match, Instant now, Arena arena) {
        if (match.startedAt().isPresent()) {
            Duration elapsed = Duration.between(match.startedAt().get(), now);
            if (elapsed.compareTo(TournamentRules.MATCH_TIMEOUT.multipliedBy(2)) > 0) {
                port.finishMatch(match.id(), match.bId(), match.aId()); // đấu trường không chốt — xử thẳng
            } else if (elapsed.compareTo(TournamentRules.MATCH_TIMEOUT) > 0) {
                arena.forfeit(match.aId()); // đấu trường chốt ⇒ onDuelSettled ghi kết quả
            }
            return;
        }
        boolean aOnline = arena.online(match.aId());
        boolean bOnline = arena.online(match.bId());
        if (aOnline && bOnline) {
            if (arena.startDuel(match.aId(), match.bId())) {
                port.markStarted(match.id(), now);
            }
        } else if (bOnline) {
            port.finishMatch(match.id(), match.bId(), match.aId());
        } else {
            port.finishMatch(match.id(), match.aId(), match.bId()); // A có mặt, hoặc cả hai vắng ⇒ A đi tiếp
        }
    }

    private void nextRound(TournamentPort.Tournament tournament, int round, Announcer announcer) {
        List<Long> alive = new ArrayList<>(port.alive(tournament.id()));
        if (alive.size() <= 1) {
            long champion = alive.isEmpty() ? 0L : alive.getFirst();
            port.update(tournament.id(), TournamentPort.Status.FINISHED, round - 1, champion);
            if (champion != 0L) {
                announcer.champion(champion);
            }
            return;
        }
        synchronized (random) {
            Collections.shuffle(alive, random);
        }
        for (int i = 0; i < alive.size(); i += 2) {
            long a = alive.get(i);
            long b = i + 1 < alive.size() ? alive.get(i + 1) : 0L;
            port.createMatch(tournament.id(), round, a, b);
            announcer.matchAssigned(a, b, round);
            if (b != 0L) {
                announcer.matchAssigned(b, a, round);
            }
        }
        port.update(tournament.id(), TournamentPort.Status.RUNNING, round, 0L);
    }
}
