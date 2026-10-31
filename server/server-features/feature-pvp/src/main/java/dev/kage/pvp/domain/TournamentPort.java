package dev.kage.pvp.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Luận võ đại hội (ADR-125, V105) — hiện thực ở server-database. */
public interface TournamentPort {

    /** Trạng thái một giải. */
    enum Status {
        OPEN,
        RUNNING,
        FINISHED
    }

    /** Kết quả đăng ký. */
    enum RegisterOutcome {
        OK,
        CLOSED,
        ALREADY_REGISTERED,
        FULL,
        LEVEL_TOO_LOW
    }

    /**
     * Một giải.
     *
     * @param championId 0 nếu chưa có / không có
     */
    record Tournament(long id, Instant startAt, Status status, int round, long championId) {}

    /**
     * Một trận.
     *
     * @param bId 0 = được đi thẳng (lẻ người)
     * @param winnerId 0 = chưa xong
     */
    record Match(long id, int round, long aId, long bId, long winnerId, Optional<Instant> startedAt) {}

    /** Giải bắt đầu lúc {@code startAt}; chưa có thì tạo ở trạng thái OPEN. */
    Tournament ensure(Instant startAt);

    /** Đăng ký trong một giao dịch (giải OPEN, chưa đầy, chưa có tên). Cấp đã kiểm ở phía gọi. */
    RegisterOutcome register(long tournamentId, long characterId, int maxEntrants);

    /** Người còn trong giải (chưa bị loại), theo thứ tự đăng ký. */
    List<Long> alive(long tournamentId);

    void update(long tournamentId, Status status, int round, long championId);

    List<Match> matches(long tournamentId, int round);

    /** Tạo một trận; {@code bId == 0} thì trận xong ngay với {@code aId} thắng. */
    void createMatch(long tournamentId, int round, long aId, long bId);

    void markStarted(long matchId, Instant startedAt);

    /** Chốt trận nếu chưa chốt (compare-and-set) + đánh dấu người thua bị loại ở vòng đó. */
    void finishMatch(long matchId, long winnerId, long loserId);

    /** Trận đang chờ chốt của giải đang chạy mà có cả hai người này. */
    Optional<Match> openMatchBetween(long firstId, long secondId);
}
