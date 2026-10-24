package dev.kage.server.database.checkin;

import java.sql.Date;
import java.time.LocalDate;

import javax.sql.DataSource;

import dev.kage.checkin.domain.OnlineRewardPolicy;
import dev.kage.checkin.domain.OnlineTimePort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link OnlineTimePort} (ADR-096, V86). Nhận mốc là MỘT câu UPDATE có điều kiện (đủ giây, bit chưa bật) —
 * hai lệnh song song không cùng thắng.
 */
public final class PostgresOnlineTimeAdapter implements OnlineTimePort {

    private static final String ADD = """
            INSERT INTO online_time (character_id, day, seconds) VALUES (?, ?, ?)
            ON CONFLICT (character_id, day) DO UPDATE SET seconds = online_time.seconds + EXCLUDED.seconds
            """;

    private static final String READ = "SELECT seconds, claimed FROM online_time WHERE character_id = ? AND day = ?";

    private static final String ENSURE_ROW = """
            INSERT INTO online_time (character_id, day) VALUES (?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String CLAIM = """
            UPDATE online_time SET claimed = claimed | ?
             WHERE character_id = ? AND day = ? AND seconds + ? >= ? AND (claimed & ?) = 0
            """;

    private final DataSource dataSource;

    public PostgresOnlineTimeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void addSeconds(long characterId, LocalDate day, int seconds) {
        JdbcOperations.update(dataSource, ADD, characterId, Date.valueOf(day), seconds);
    }

    @Override
    public Day read(long characterId, LocalDate day) {
        return JdbcOperations.queryOne(
                        dataSource, READ, rs -> new Day(rs.getInt(1), rs.getInt(2)), characterId, Date.valueOf(day))
                .orElse(new Day(0, 0));
    }

    @Override
    public boolean claim(long characterId, LocalDate day, OnlineRewardPolicy.Milestone milestone, int liveSeconds) {
        JdbcOperations.update(dataSource, ENSURE_ROW, characterId, Date.valueOf(day));
        return JdbcOperations.update(
                        dataSource,
                        CLAIM,
                        milestone.mask(),
                        characterId,
                        Date.valueOf(day),
                        liveSeconds,
                        milestone.requiredSeconds(),
                        milestone.mask())
                > 0;
    }
}
