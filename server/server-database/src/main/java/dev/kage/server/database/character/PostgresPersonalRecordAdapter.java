package dev.kage.server.database.character;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.character.domain.PersonalRecordPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link PersonalRecordPort} (ADR-240, V168). Nâng bằng upsert có điều kiện — không bao giờ hạ. */
public final class PostgresPersonalRecordAdapter implements PersonalRecordPort {

    private static final String SELECT = "SELECT kind, value FROM personal_record WHERE character_id = ?";
    private static final String RAISE = """
            INSERT INTO personal_record (character_id, kind, value, achieved_at) VALUES (?, ?, ?, ?)
            ON CONFLICT (character_id, kind) DO UPDATE SET value = EXCLUDED.value, achieved_at = EXCLUDED.achieved_at
             WHERE personal_record.value < EXCLUDED.value
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresPersonalRecordAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<Kind, Long> recordsOf(long characterId) {
        Map<Kind, Long> records = new EnumMap<>(Kind.class);
        for (Map.Entry<String, Long> row : JdbcOperations.query(
                dataSource, SELECT, rs -> Map.entry(rs.getString("kind"), rs.getLong("value")), characterId)) {
            records.put(Kind.valueOf(row.getKey()), row.getValue());
        }
        return records;
    }

    @Override
    public boolean raise(long characterId, Kind kind, long value, Instant at) {
        return JdbcOperations.update(dataSource, RAISE, characterId, kind.name(), value, Timestamp.from(at)) > 0;
    }
}
