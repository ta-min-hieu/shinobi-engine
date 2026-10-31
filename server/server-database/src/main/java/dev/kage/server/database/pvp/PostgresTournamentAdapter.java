package dev.kage.server.database.pvp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.pvp.domain.TournamentPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link TournamentPort} (ADR-125, V105). */
public final class PostgresTournamentAdapter implements TournamentPort {

    private static final String ENSURE = """
            INSERT INTO tournament (start_at, status) VALUES (?, 'OPEN') ON CONFLICT (start_at) DO NOTHING
            """;
    private static final String FIND =
            "SELECT id, start_at, status, round, champion_id FROM tournament WHERE start_at = ?";
    private static final String LOCK = "SELECT status FROM tournament WHERE id = ? FOR UPDATE";
    private static final String COUNT = "SELECT COUNT(*) FROM tournament_entry WHERE tournament_id = ?";
    private static final String ENTER = """
            INSERT INTO tournament_entry (tournament_id, character_id) VALUES (?, ?) ON CONFLICT DO NOTHING
            """;
    private static final String ALIVE = """
            SELECT character_id FROM tournament_entry
             WHERE tournament_id = ? AND eliminated_round IS NULL
             ORDER BY registered_at, character_id
            """;
    private static final String UPDATE = "UPDATE tournament SET status = ?, round = ?, champion_id = ? WHERE id = ?";
    private static final String MATCHES = """
            SELECT id, round, a_id, COALESCE(b_id, 0), COALESCE(winner_id, 0), started_at
              FROM tournament_match WHERE tournament_id = ? AND round = ? ORDER BY id
            """;
    private static final String CREATE = """
            INSERT INTO tournament_match (tournament_id, round, a_id, b_id, winner_id) VALUES (?, ?, ?, ?, ?)
            """;
    private static final String START = "UPDATE tournament_match SET started_at = ? WHERE id = ?";
    private static final String FINISH = """
            WITH done AS (
                UPDATE tournament_match SET winner_id = ? WHERE id = ? AND winner_id IS NULL
                RETURNING tournament_id, round)
            UPDATE tournament_entry e SET eliminated_round = done.round
              FROM done
             WHERE e.tournament_id = done.tournament_id AND e.character_id = ?
            """;
    private static final String OPEN_BETWEEN = """
            SELECT m.id, m.round, m.a_id, COALESCE(m.b_id, 0), COALESCE(m.winner_id, 0), m.started_at
              FROM tournament_match m JOIN tournament t ON t.id = m.tournament_id
             WHERE t.status = 'RUNNING' AND m.winner_id IS NULL
               AND ((m.a_id = ? AND m.b_id = ?) OR (m.a_id = ? AND m.b_id = ?))
             ORDER BY m.id DESC LIMIT 1
            """;

    private final DataSource dataSource;

    public PostgresTournamentAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Tournament ensure(Instant startAt) {
        Timestamp at = Timestamp.from(startAt);
        JdbcOperations.update(dataSource, ENSURE, at);
        return JdbcOperations.queryOne(dataSource, FIND, PostgresTournamentAdapter::tournament, at)
                .orElseThrow();
    }

    @Override
    public RegisterOutcome register(long tournamentId, long characterId, int maxEntrants) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            String status = JdbcOperations.queryOne(connection, LOCK, rs -> rs.getString(1), tournamentId)
                    .orElse("");
            if (!Status.OPEN.name().equals(status)) {
                return RegisterOutcome.CLOSED;
            }
            long entrants = JdbcOperations.queryOne(connection, COUNT, rs -> rs.getLong(1), tournamentId)
                    .orElse(0L);
            if (entrants >= maxEntrants) {
                return RegisterOutcome.FULL;
            }
            return JdbcOperations.update(connection, ENTER, tournamentId, characterId) == 1
                    ? RegisterOutcome.OK
                    : RegisterOutcome.ALREADY_REGISTERED;
        });
    }

    @Override
    public List<Long> alive(long tournamentId) {
        return JdbcOperations.query(dataSource, ALIVE, rs -> rs.getLong(1), tournamentId);
    }

    @Override
    public void update(long tournamentId, Status status, int round, long championId) {
        JdbcOperations.update(
                dataSource, UPDATE, status.name(), round, championId == 0L ? null : championId, tournamentId);
    }

    @Override
    public List<Match> matches(long tournamentId, int round) {
        return JdbcOperations.query(dataSource, MATCHES, PostgresTournamentAdapter::match, tournamentId, round);
    }

    @Override
    public void createMatch(long tournamentId, int round, long aId, long bId) {
        JdbcOperations.update(
                dataSource, CREATE, tournamentId, round, aId, bId == 0L ? null : bId, bId == 0L ? aId : null);
    }

    @Override
    public void markStarted(long matchId, Instant startedAt) {
        JdbcOperations.update(dataSource, START, Timestamp.from(startedAt), matchId);
    }

    @Override
    public void finishMatch(long matchId, long winnerId, long loserId) {
        JdbcOperations.update(dataSource, FINISH, winnerId, matchId, loserId);
    }

    @Override
    public Optional<Match> openMatchBetween(long firstId, long secondId) {
        return JdbcOperations.queryOne(
                dataSource, OPEN_BETWEEN, PostgresTournamentAdapter::match, firstId, secondId, secondId, firstId);
    }

    private static Tournament tournament(ResultSet rs) throws SQLException {
        return new Tournament(
                rs.getLong(1),
                rs.getTimestamp(2).toInstant(),
                Status.valueOf(rs.getString(3)),
                rs.getInt(4),
                rs.getLong(5));
    }

    private static Match match(ResultSet rs) throws SQLException {
        Timestamp started = rs.getTimestamp(6);
        return new Match(
                rs.getLong(1),
                rs.getInt(2),
                rs.getLong(3),
                rs.getLong(4),
                rs.getLong(5),
                Optional.ofNullable(started).map(Timestamp::toInstant));
    }
}
