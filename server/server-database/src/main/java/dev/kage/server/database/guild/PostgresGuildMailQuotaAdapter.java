package dev.kage.server.database.guild;

import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildMailQuotaPort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link GuildMailQuotaPort} (ADR-235) trên {@code audit_log} hành động {@code GUILD_MAIL} — không bảng
 * mới; audit cũng là dấu vết cho CSKH khi có người kêu bị bang chủ spam thư.
 */
public final class PostgresGuildMailQuotaAdapter implements GuildMailQuotaPort {

    private static final String COUNT = """
            SELECT COUNT(*) FROM audit_log
             WHERE action = 'GUILD_MAIL' AND created_at >= ?
               AND (detail_json::jsonb ->> 'guildId')::bigint = ?
            """;
    private static final String INSERT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, 'GUILD_MAIL', ?, ?)
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildMailQuotaAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int sentSince(long guildId, Instant since) {
        return JdbcOperations.queryOne(dataSource, COUNT, rs -> rs.getInt(1), Timestamp.from(since), guildId)
                .orElse(0);
    }

    @Override
    public void record(long guildId, long senderId, int recipients, Instant at) {
        JdbcOperations.update(
                dataSource,
                INSERT,
                senderId,
                "{\"guildId\":" + guildId + ",\"recipients\":" + recipients + "}",
                Timestamp.from(at));
    }
}
