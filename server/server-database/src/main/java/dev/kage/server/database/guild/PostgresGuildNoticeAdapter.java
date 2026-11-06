package dev.kage.server.database.guild;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildNoticePort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link GuildNoticePort} (ADR-179, V142). */
public final class PostgresGuildNoticeAdapter implements GuildNoticePort {

    private static final String NOTICE = """
            SELECT g.notice FROM guild g JOIN guild_member m ON m.guild_id = g.id WHERE m.character_id = ?
            """;

    private static final String ROLE = "SELECT guild_id, role FROM guild_member WHERE character_id = ?";

    private static final String UPDATE = "UPDATE guild SET notice = ?, notice_updated_at = ? WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Thành viên bang. */
    private record Membership(long guildId, String role) {}

    private final DataSource dataSource;

    public PostgresGuildNoticeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<String> noticeOf(long characterId) {
        return JdbcOperations.queryOne(dataSource, NOTICE, rs -> rs.getString(1), characterId);
    }

    @Override
    public Outcome set(long characterId, String notice, Instant at) {
        return JdbcTransactionRunner.call(dataSource, connection -> setIn(connection, characterId, notice, at));
    }

    private static Outcome setIn(Connection connection, long characterId, String notice, Instant at) {
        Optional<Membership> membership = JdbcOperations.queryOne(
                connection, ROLE, rs -> new Membership(rs.getLong(1), rs.getString(2)), characterId);
        if (membership.isEmpty()) {
            return Outcome.NOT_IN_GUILD;
        }
        if (!"LEADER".equals(membership.get().role())) {
            return Outcome.NOT_LEADER;
        }
        long guildId = membership.get().guildId();
        JdbcOperations.update(connection, UPDATE, notice, Timestamp.from(at), guildId);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "GUILD_NOTICE_SET",
                "{\"guildId\":" + guildId + ",\"length\":" + notice.length() + "}",
                Timestamp.from(at));
        return Outcome.OK;
    }
}
