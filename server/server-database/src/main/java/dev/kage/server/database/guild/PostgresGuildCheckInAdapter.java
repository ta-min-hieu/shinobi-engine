package dev.kage.server.database.guild;

import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildCheckInPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link GuildCheckInPort} (ADR-186, V147). */
public final class PostgresGuildCheckInAdapter implements GuildCheckInPort {

    private static final String GUILD_OF = "SELECT guild_id FROM guild_member WHERE character_id = ?";
    private static final String LEVEL_OF = "SELECT level FROM guild WHERE id = ?";

    private static final String INSERT = """
            INSERT INTO guild_checkin (character_id, day, guild_id, created_at) VALUES (?, ?, ?, ?)
            ON CONFLICT (character_id, day) DO NOTHING
            """;

    private static final String ADD_CONTRIBUTION =
            "UPDATE guild_member SET contribution = contribution + ? WHERE character_id = ?";

    private static final String TODAY_COUNT = "SELECT COUNT(*) FROM guild_checkin WHERE guild_id = ? AND day = ?";

    private final DataSource dataSource;

    public PostgresGuildCheckInAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CheckIn checkIn(long characterId, LocalDate day, Instant at) {
        return JdbcTransactionRunner.call(dataSource, connection -> checkInIn(connection, characterId, day, at));
    }

    private static CheckIn checkInIn(Connection connection, long characterId, LocalDate day, Instant at) {
        Optional<Long> guild = JdbcOperations.queryOne(connection, GUILD_OF, rs -> rs.getLong(1), characterId);
        if (guild.isEmpty()) {
            return new CheckIn(Outcome.NOT_IN_GUILD, 0, false, 0L);
        }
        long guildId = guild.get();
        int inserted =
                JdbcOperations.update(connection, INSERT, characterId, Date.valueOf(day), guildId, Timestamp.from(at));
        if (inserted == 0) {
            return new CheckIn(Outcome.ALREADY_CHECKED_IN, todayCount(connection, guildId, day), false, guildId);
        }
        int levelBefore = JdbcOperations.queryOne(connection, LEVEL_OF, rs -> rs.getInt(1), guildId)
                .orElse(1);
        JdbcOperations.update(connection, ADD_CONTRIBUTION, CONTRIBUTION, characterId);
        int levelAfter = PostgresGuildProgressionAdapter.addGuildExp(connection, guildId, GUILD_EXP);
        if (levelAfter > levelBefore) {
            GuildLog.append(
                    connection,
                    new GuildLog.Entry(guildId, GuildLog.Kind.LEVEL_UP, characterId, 0L, levelAfter, ""),
                    at);
        }
        return new CheckIn(Outcome.OK, todayCount(connection, guildId, day), levelAfter > levelBefore, guildId);
    }

    private static int todayCount(Connection connection, long guildId, LocalDate day) {
        return JdbcOperations.queryOne(connection, TODAY_COUNT, rs -> rs.getInt(1), guildId, Date.valueOf(day))
                .orElse(0);
    }
}
