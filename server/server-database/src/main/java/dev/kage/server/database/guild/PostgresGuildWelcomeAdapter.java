package dev.kage.server.database.guild;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildWelcomePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link GuildWelcomePort} (ADR-313) — cột {@code guild.welcome_message} (V174). */
public final class PostgresGuildWelcomeAdapter implements GuildWelcomePort {

    private static final String GET = "SELECT welcome_message FROM guild WHERE id = ?";
    private static final String SET = "UPDATE guild SET welcome_message = ? WHERE id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildWelcomeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String welcomeOf(long guildId) {
        return JdbcOperations.queryOne(dataSource, GET, rs -> rs.getString(1), guildId)
                .orElse("");
    }

    @Override
    public void setWelcome(long guildId, String message) {
        JdbcOperations.update(dataSource, SET, message, guildId);
    }
}
