package dev.kage.server.database.achievement;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import dev.kage.achievement.domain.Achievement;
import dev.kage.achievement.domain.AchievementKind;
import dev.kage.achievement.domain.AchievementPort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link AchievementPort} (ADR-062, V64). Bảng định nghĩa đọc MỘT lần (tham số, đổi thì khởi động
 * lại — cùng luật catalog quái/NPC).
 */
public final class PostgresAchievementAdapter implements AchievementPort {

    private static final String DEFINITIONS =
            "SELECT code, kind, target, name_key, title_key FROM achievement ORDER BY sort_order, code";

    private static final String ADD_TO_COUNTER = """
            INSERT INTO character_achievement_counter (character_id, kind, value) VALUES (?, ?, ?)
            ON CONFLICT (character_id, kind)
            DO UPDATE SET value = character_achievement_counter.value + EXCLUDED.value
            RETURNING value
            """;

    private static final String COUNTERS =
            "SELECT kind, value FROM character_achievement_counter WHERE character_id = ?";

    private static final String UNLOCK = """
            INSERT INTO character_achievement (character_id, achievement_code, unlocked_at) VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String UNLOCKED = "SELECT achievement_code FROM character_achievement WHERE character_id = ?";

    private static final String UNLOCKED_COUNTS =
            "SELECT character_id, COUNT(*) AS n FROM character_achievement GROUP BY character_id";

    private static final String ACTIVE_TITLE = "SELECT achievement_code FROM character_title WHERE character_id = ?";

    /** Đeo CHỈ khi đã mở — điều kiện nằm trong chính câu lệnh (D2). */
    private static final String SET_TITLE = """
            INSERT INTO character_title (character_id, achievement_code)
            SELECT character_id, achievement_code FROM character_achievement
            WHERE character_id = ? AND achievement_code = ?
            ON CONFLICT (character_id) DO UPDATE SET achievement_code = EXCLUDED.achievement_code
            """;

    private static final String CLEAR_TITLE = "DELETE FROM character_title WHERE character_id = ?";

    private final DataSource dataSource;
    private volatile List<Achievement> definitions;

    public PostgresAchievementAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Achievement> definitions() {
        List<Achievement> loaded = definitions;
        if (loaded == null) {
            loaded = List.copyOf(JdbcOperations.query(
                    dataSource,
                    DEFINITIONS,
                    rs -> new Achievement(
                            rs.getString("code"),
                            AchievementKind.valueOf(rs.getString("kind")),
                            rs.getLong("target"),
                            rs.getString("name_key"),
                            rs.getString("title_key"))));
            definitions = loaded;
        }
        return loaded;
    }

    @Override
    public long addToCounter(long characterId, AchievementKind kind, long delta) {
        return JdbcOperations.queryOne(
                        dataSource, ADD_TO_COUNTER, rs -> rs.getLong("value"), characterId, kind.name(), delta)
                .orElseThrow();
    }

    @Override
    public Map<AchievementKind, Long> counters(long characterId) {
        Map<AchievementKind, Long> counters = new EnumMap<>(AchievementKind.class);
        JdbcOperations.query(
                        dataSource,
                        COUNTERS,
                        rs -> Map.entry(AchievementKind.valueOf(rs.getString("kind")), rs.getLong("value")),
                        characterId)
                .forEach(entry -> counters.put(entry.getKey(), entry.getValue()));
        return counters;
    }

    @Override
    public boolean unlock(long characterId, String achievementCode) {
        return JdbcOperations.update(
                        dataSource, UNLOCK, characterId, achievementCode, OffsetDateTime.now(ZoneOffset.UTC))
                == 1;
    }

    @Override
    public Set<String> unlocked(long characterId) {
        return new HashSet<>(
                JdbcOperations.query(dataSource, UNLOCKED, rs -> rs.getString("achievement_code"), characterId));
    }

    @Override
    public Map<Long, Integer> unlockedCounts() {
        Map<Long, Integer> counts = new HashMap<>();
        for (Map.Entry<Long, Integer> row : JdbcOperations.query(
                dataSource, UNLOCKED_COUNTS, rs -> Map.entry(rs.getLong("character_id"), rs.getInt("n")))) {
            counts.put(row.getKey(), row.getValue());
        }
        return counts;
    }

    @Override
    public Optional<String> activeTitle(long characterId) {
        return JdbcOperations.queryOne(dataSource, ACTIVE_TITLE, rs -> rs.getString("achievement_code"), characterId);
    }

    @Override
    public boolean setActiveTitle(long characterId, String achievementCode) {
        return JdbcOperations.update(dataSource, SET_TITLE, characterId, achievementCode) == 1;
    }

    @Override
    public void clearActiveTitle(long characterId) {
        JdbcOperations.update(dataSource, CLEAR_TITLE, characterId);
    }
}
