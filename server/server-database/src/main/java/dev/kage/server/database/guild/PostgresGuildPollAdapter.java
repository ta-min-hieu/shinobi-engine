package dev.kage.server.database.guild;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildPollPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link GuildPollPort} (ADR-248, V169). */
public final class PostgresGuildPollAdapter implements GuildPollPort {

    private static final String MEMBER = "SELECT guild_id, role FROM guild_member WHERE character_id = ?";
    private static final String LOCK_GUILD = "SELECT id FROM guild WHERE id = ? FOR UPDATE";
    private static final String CLOSE_OPEN = "UPDATE guild_poll SET closes_at = ? WHERE guild_id = ? AND closes_at > ?";
    private static final String INSERT = """
            INSERT INTO guild_poll (guild_id, created_by, question, options, created_at, closes_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String OPEN_POLL = """
            SELECT id, cardinality(options) AS option_count FROM guild_poll
             WHERE guild_id = ? AND closes_at > ? ORDER BY id DESC LIMIT 1
            """;
    private static final String UPSERT_VOTE = """
            INSERT INTO guild_poll_vote (poll_id, character_id, option_index) VALUES (?, ?, ?)
            ON CONFLICT (poll_id, character_id) DO UPDATE SET option_index = EXCLUDED.option_index
            """;
    private static final String LATEST = """
            SELECT p.id, p.question, p.options, p.closes_at
              FROM guild_poll p JOIN guild_member gm ON gm.guild_id = p.guild_id
             WHERE gm.character_id = ?
             ORDER BY p.id DESC LIMIT 1
            """;
    private static final String COUNTS =
            "SELECT option_index, COUNT(*) AS n FROM guild_poll_vote WHERE poll_id = ? GROUP BY option_index";
    private static final String MY_VOTE =
            "SELECT option_index FROM guild_poll_vote WHERE poll_id = ? AND character_id = ?";

    /** Thành viên: bang + vai trò. */
    private record Membership(long guildId, String role) {
        boolean officer() {
            return "LEADER".equals(role) || "VICE_LEADER".equals(role);
        }
    }

    /** Dòng cuộc bình chọn (chưa đếm phiếu). */
    private record PollRow(long id, String question, List<String> options, Instant closesAt) {}

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildPollAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CreateOutcome create(Draft draft) {
        return JdbcTransactionRunner.call(dataSource, connection -> createIn(connection, draft));
    }

    private static CreateOutcome createIn(Connection connection, Draft draft) {
        Optional<Membership> member = membership(connection, draft.characterId());
        if (member.isEmpty()) {
            return CreateOutcome.NOT_IN_GUILD;
        }
        if (!member.get().officer()) {
            return CreateOutcome.NOT_OFFICER;
        }
        long guildId = member.get().guildId();
        JdbcOperations.queryOne(connection, LOCK_GUILD, rs -> rs.getLong(1), guildId);
        Timestamp now = Timestamp.from(draft.now());
        JdbcOperations.update(connection, CLOSE_OPEN, now, guildId, now);
        JdbcOperations.update(
                connection,
                INSERT,
                guildId,
                draft.characterId(),
                draft.question(),
                textArray(connection, draft.options()),
                now,
                Timestamp.from(draft.closesAt()));
        return CreateOutcome.OK;
    }

    @Override
    public VoteOutcome vote(long characterId, int option, Instant now) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            Optional<Membership> member = membership(connection, characterId);
            if (member.isEmpty()) {
                return VoteOutcome.NOT_IN_GUILD;
            }
            Optional<long[]> open = JdbcOperations.queryOne(
                    connection,
                    OPEN_POLL,
                    rs -> new long[] {rs.getLong("id"), rs.getInt("option_count")},
                    member.get().guildId(),
                    Timestamp.from(now));
            if (open.isEmpty()) {
                return VoteOutcome.NO_OPEN_POLL;
            }
            if (option < 0 || option >= open.get()[1]) {
                return VoteOutcome.BAD_OPTION;
            }
            JdbcOperations.update(connection, UPSERT_VOTE, open.get()[0], characterId, option);
            return VoteOutcome.OK;
        });
    }

    @Override
    public Optional<Poll> latest(long characterId) {
        Optional<PollRow> row = JdbcOperations.queryOne(
                dataSource,
                LATEST,
                rs -> new PollRow(
                        rs.getLong("id"),
                        rs.getString("question"),
                        textList(rs.getArray("options")),
                        rs.getTimestamp("closes_at").toInstant()),
                characterId);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        PollRow poll = row.get();
        Integer[] counts = new Integer[poll.options().size()];
        Arrays.fill(counts, 0);
        for (int[] count : JdbcOperations.query(
                dataSource, COUNTS, rs -> new int[] {rs.getInt("option_index"), rs.getInt("n")}, poll.id())) {
            if (count[0] < counts.length) {
                counts[count[0]] = count[1];
            }
        }
        int mine = JdbcOperations.queryOne(dataSource, MY_VOTE, rs -> rs.getInt(1), poll.id(), characterId)
                .orElse(-1);
        return Optional.of(
                new Poll(poll.id(), poll.question(), poll.options(), List.of(counts), poll.closesAt(), mine));
    }

    private static Optional<Membership> membership(Connection connection, long characterId) {
        return JdbcOperations.queryOne(
                connection, MEMBER, rs -> new Membership(rs.getLong("guild_id"), rs.getString("role")), characterId);
    }

    private static Array textArray(Connection connection, List<String> values) {
        try {
            return connection.createArrayOf("varchar", values.toArray(String[]::new));
        } catch (SQLException e) {
            throw new dev.kage.server.database.DatabaseAccessException("Không dựng được mảng lựa chọn bình chọn", e);
        }
    }

    private static List<String> textList(Array array) throws SQLException {
        List<String> values = new ArrayList<>();
        for (Object value : (Object[]) array.getArray()) {
            values.add((String) value);
        }
        return values;
    }
}
