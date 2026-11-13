package dev.kage.server.database.character;

import java.time.LocalDate;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.character.domain.LoginStreakPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.RowMapper;

/**
 * Hiện thực {@link LoginStreakPort} (ADR-327) — bảng {@code login_streak} (V176). Một câu UPSERT có điều kiện: chỉ ghi
 * khi ngày mới sau ngày đã lưu, nên hai lần vào world cùng ngày (kể cả song song) chỉ tính một.
 */
public final class PostgresLoginStreakAdapter implements LoginStreakPort {

    private static final String ADVANCE = """
            INSERT INTO login_streak AS s (character_id, streak, best, last_day) VALUES (?, 1, 1, ?)
            ON CONFLICT (character_id) DO UPDATE SET
                streak = CASE WHEN s.last_day = EXCLUDED.last_day - 1 THEN s.streak + 1 ELSE 1 END,
                best = GREATEST(s.best, CASE WHEN s.last_day = EXCLUDED.last_day - 1 THEN s.streak + 1 ELSE 1 END),
                last_day = EXCLUDED.last_day
            WHERE s.last_day < EXCLUDED.last_day
            RETURNING streak, best, last_day
            """;
    private static final String FIND = "SELECT streak, best, last_day FROM login_streak WHERE character_id = ?";
    private static final RowMapper<Row> ROW =
            rs -> new Row(rs.getInt("streak"), rs.getInt("best"), rs.getObject("last_day", LocalDate.class));

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresLoginStreakAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Row> advance(long characterId, LocalDate today) {
        return JdbcOperations.queryOne(dataSource, ADVANCE, ROW, characterId, today);
    }

    @Override
    public Optional<Row> find(long characterId) {
        return JdbcOperations.queryOne(dataSource, FIND, ROW, characterId);
    }
}
