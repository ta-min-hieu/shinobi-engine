package dev.kage.server.database.checkin;

import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import dev.kage.checkin.domain.ActivityPort;
import dev.kage.checkin.domain.ActivityRules;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link ActivityPort} (ADR-106, V89). Nhận rương tính lại điểm TRONG giao dịch rồi mới chèn dòng nhận —
 * khoá chính {@code (character_id, day, milestone)} chặn nhận đôi kể cả khi hai lệnh tới cùng lúc.
 */
public final class PostgresActivityAdapter implements ActivityPort {

    private static final String DEFINITIONS = """
            SELECT code, name_key, points_each, max_count FROM activity_definition ORDER BY sort_order, code
            """;

    private static final String CHESTS = """
            SELECT milestone, gold, COALESCE(item_template_id, 0), quantity FROM activity_chest ORDER BY milestone
            """;

    private static final String PROGRESS =
            "SELECT activity_code, count FROM activity_progress WHERE character_id = ? AND day = ?";

    // Mã lạ vi phạm FK ⇒ câu SELECT lọc trước, không chèn gì (ghi là việc phụ, không được ném).
    private static final String RECORD = """
            INSERT INTO activity_progress (character_id, day, activity_code, count)
            SELECT ?, ?, code, 1 FROM activity_definition WHERE code = ?
            ON CONFLICT (character_id, day, activity_code) DO UPDATE SET count = activity_progress.count + 1
            """;

    private static final String CLAIMED =
            "SELECT milestone FROM activity_chest_claim WHERE character_id = ? AND day = ?";

    private static final String INSERT_CLAIM = """
            INSERT INTO activity_chest_claim (character_id, day, milestone, claimed_at) VALUES (?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private final DataSource dataSource;

    public PostgresActivityAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Definition> definitions() {
        return JdbcOperations.query(
                dataSource,
                DEFINITIONS,
                rs -> new Definition(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4)));
    }

    @Override
    public List<Chest> chests() {
        return JdbcOperations.query(
                dataSource, CHESTS, rs -> new Chest(rs.getInt(1), rs.getLong(2), rs.getLong(3), rs.getInt(4)));
    }

    /** Một dòng tiến độ. */
    private record Row(String code, int count) {}

    @Override
    public Map<String, Integer> progress(long characterId, LocalDate day) {
        return toMap(JdbcOperations.query(
                dataSource, PROGRESS, rs -> new Row(rs.getString(1), rs.getInt(2)), characterId, Date.valueOf(day)));
    }

    private static Map<String, Integer> progressIn(Connection connection, long characterId, LocalDate day) {
        return toMap(JdbcOperations.query(
                connection, PROGRESS, rs -> new Row(rs.getString(1), rs.getInt(2)), characterId, Date.valueOf(day)));
    }

    private static Map<String, Integer> toMap(List<Row> rows) {
        Map<String, Integer> progress = new HashMap<>();
        rows.forEach(row -> progress.put(row.code(), row.count()));
        return progress;
    }

    @Override
    public void record(long characterId, LocalDate day, String activityCode) {
        JdbcOperations.update(dataSource, RECORD, characterId, Date.valueOf(day), activityCode);
    }

    @Override
    public Set<Integer> claimed(long characterId, LocalDate day) {
        return new HashSet<>(
                JdbcOperations.query(dataSource, CLAIMED, rs -> rs.getInt(1), characterId, Date.valueOf(day)));
    }

    @Override
    public Optional<Chest> claim(long characterId, LocalDate day, int milestone, Instant at) {
        Optional<Chest> chest =
                chests().stream().filter(c -> c.milestone() == milestone).findFirst();
        if (chest.isEmpty()) {
            return Optional.empty();
        }
        List<Definition> definitions = definitions();
        return JdbcTransactionRunner.call(dataSource, connection -> {
            int points = ActivityRules.totalPoints(definitions, progressIn(connection, characterId, day));
            if (points < milestone) {
                return Optional.empty();
            }
            int inserted = JdbcOperations.update(
                    connection, INSERT_CLAIM, characterId, Date.valueOf(day), milestone, Timestamp.from(at));
            return inserted > 0 ? chest : Optional.empty();
        });
    }
}
