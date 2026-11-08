package dev.kage.server.database.guild;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildBuffPort;
import dev.kage.guild.domain.GuildLevelTable;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link GuildBuffPort} (ADR-239, V168). Khoá dòng bang → kiểm → trừ kinh nghiệm + đặt hạn → audit. */
public final class PostgresGuildBuffAdapter implements GuildBuffPort {

    private static final String LOCK = """
            SELECT g.id, g.leader_character_id, g.exp, g.level, g.exp_buff_until
              FROM guild g JOIN guild_member gm ON gm.guild_id = g.id
             WHERE gm.character_id = ?
               FOR UPDATE OF g
            """;
    private static final String APPLY = "UPDATE guild SET exp = exp - ?, exp_buff_until = ? WHERE id = ?";
    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, 'GUILD_BUFF', ?, ?)
            """;
    private static final String UNTIL = """
            SELECT g.exp_buff_until FROM guild g JOIN guild_member gm ON gm.guild_id = g.id WHERE gm.character_id = ?
            """;

    /** Dòng bang đã khoá. */
    private record Row(long guildId, long leaderId, long exp, int level, Instant until) {}

    /** Tham số một lần bật. */
    private record Request(long leaderId, long cost, Instant until, Instant now) {}

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildBuffAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Activation activate(long leaderId, long cost, Instant until, Instant now) {
        Request request = new Request(leaderId, cost, until, now);
        return JdbcTransactionRunner.call(dataSource, connection -> activateIn(connection, request));
    }

    private static Activation activateIn(Connection connection, Request request) {
        Optional<Row> row = JdbcOperations.queryOne(
                connection,
                LOCK,
                rs -> new Row(
                        rs.getLong("id"),
                        rs.getLong("leader_character_id"),
                        rs.getLong("exp"),
                        rs.getInt("level"),
                        Optional.ofNullable(rs.getTimestamp("exp_buff_until"))
                                .map(Timestamp::toInstant)
                                .orElse(Instant.EPOCH)),
                request.leaderId());
        if (row.isEmpty()) {
            return new Activation(Outcome.NOT_IN_GUILD, Instant.EPOCH, 0L, 0L);
        }
        Row guild = row.get();
        Outcome refused = check(guild, request);
        if (refused != Outcome.OK) {
            return new Activation(refused, guild.until(), guild.exp(), guild.guildId());
        }
        JdbcOperations.update(connection, APPLY, request.cost(), Timestamp.from(request.until()), guild.guildId());
        JdbcOperations.update(
                connection,
                AUDIT,
                request.leaderId(),
                "{\"guildId\":" + guild.guildId() + ",\"cost\":" + request.cost() + "}",
                Timestamp.from(request.now()));
        return new Activation(Outcome.OK, request.until(), guild.exp() - request.cost(), guild.guildId());
    }

    /** Chỉ bang chủ; buff cũ còn hạn thì thôi; trừ xong không được tụt dưới mốc cấp hiện tại. */
    private static Outcome check(Row guild, Request request) {
        if (guild.leaderId() != request.leaderId()) {
            return Outcome.NOT_LEADER;
        }
        if (request.now().isBefore(guild.until())) {
            return Outcome.ALREADY_ACTIVE;
        }
        return guild.exp() - request.cost() < GuildLevelTable.expToReach(guild.level())
                ? Outcome.NOT_ENOUGH_EXP
                : Outcome.OK;
    }

    @Override
    public Optional<Instant> buffUntilOf(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        UNTIL,
                        rs -> Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant),
                        characterId)
                .flatMap(until -> until);
    }
}
