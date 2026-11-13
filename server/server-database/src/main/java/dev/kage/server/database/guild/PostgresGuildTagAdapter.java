package dev.kage.server.database.guild;

import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildTagPort;
import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link GuildTagPort} (ADR-344) — cột {@code guild.tag} (V178, UNIQUE lower(tag)). */
public final class PostgresGuildTagAdapter implements GuildTagPort {

    private static final String TAG = "SELECT tag FROM guild WHERE id = ? AND tag IS NOT NULL";
    private static final String TAG_OF_CHARACTER = """
            SELECT g.tag FROM guild g JOIN guild_member m ON m.guild_id = g.id
             WHERE m.character_id = ? AND g.tag IS NOT NULL
            """;
    private static final String TAKEN = "SELECT 1 FROM guild WHERE lower(tag) = lower(?) AND id <> ?";
    private static final String SET = "UPDATE guild SET tag = ? WHERE id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildTagAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<String> tagOf(long guildId) {
        return JdbcOperations.queryOne(dataSource, TAG, rs -> rs.getString(1), guildId);
    }

    @Override
    public Optional<String> tagOfCharacter(long characterId) {
        return JdbcOperations.queryOne(dataSource, TAG_OF_CHARACTER, rs -> rs.getString(1), characterId);
    }

    @Override
    public boolean setTag(long guildId, String tag) {
        if (tag != null
                && JdbcOperations.queryOne(dataSource, TAKEN, rs -> rs.getInt(1), tag, guildId)
                        .isPresent()) {
            return false;
        }
        try {
            return JdbcOperations.update(dataSource, SET, tag, guildId) == 1;
        } catch (DatabaseAccessException e) {
            // Hai bang cùng đặt một ký hiệu song song: chỉ mục UNIQUE chặn người đến sau.
            return false;
        }
    }
}
