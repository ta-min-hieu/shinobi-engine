package dev.kage.server.database.world;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.world.domain.TowerPort;

/**
 * Hiện thực {@link TowerPort} (ADR-113, V93). Ghi tầng là MỘT câu UPSERT có điều kiện {@code best_floor < floor}: hai
 * lần ghi cùng tầng song song chỉ một lần trả dòng, nên thưởng mốc không bao giờ trao đôi.
 */
public final class PostgresTowerAdapter implements TowerPort {

    private static final String FLOOR = "SELECT floor, monster_code, monster_count FROM tower_floor WHERE floor = ?";
    private static final String TOP_FLOOR = "SELECT COALESCE(MAX(floor), 0) FROM tower_floor";
    private static final String BEST = "SELECT best_floor FROM character_tower WHERE character_id = ?";

    private static final String RECORD = """
            INSERT INTO character_tower (character_id, best_floor, updated_at) VALUES (?, ?, ?)
            ON CONFLICT (character_id) DO UPDATE SET best_floor = EXCLUDED.best_floor, updated_at = EXCLUDED.updated_at
             WHERE character_tower.best_floor < EXCLUDED.best_floor
            RETURNING best_floor
            """;

    private static final String REWARD =
            "SELECT floor, gold, COALESCE(item_template_id, 0), quantity FROM tower_reward WHERE floor = ?";

    private static final String TOP = """
            SELECT c.name, t.best_floor FROM character_tower t JOIN character c ON c.id = t.character_id
             WHERE t.best_floor > 0
             ORDER BY t.best_floor DESC, t.updated_at, c.id
             LIMIT ?
            """;

    private final DataSource dataSource;

    public PostgresTowerAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Floor> floor(int floor) {
        return JdbcOperations.queryOne(
                dataSource, FLOOR, rs -> new Floor(rs.getInt(1), rs.getString(2), rs.getInt(3)), floor);
    }

    @Override
    public int topFloor() {
        return JdbcOperations.queryOne(dataSource, TOP_FLOOR, rs -> rs.getInt(1))
                .orElse(0);
    }

    @Override
    public int bestFloorOf(long characterId) {
        return JdbcOperations.queryOne(dataSource, BEST, rs -> rs.getInt(1), characterId)
                .orElse(0);
    }

    @Override
    public Optional<Reward> recordCleared(long characterId, int floor) {
        boolean advanced = JdbcOperations.queryOne(
                        dataSource, RECORD, rs -> rs.getInt(1), characterId, floor, Timestamp.from(Instant.now()))
                .isPresent();
        if (!advanced) {
            return Optional.empty();
        }
        return JdbcOperations.queryOne(
                dataSource, REWARD, rs -> new Reward(rs.getInt(1), rs.getLong(2), rs.getLong(3), rs.getInt(4)), floor);
    }

    @Override
    public List<Climber> top(int limit) {
        return JdbcOperations.query(dataSource, TOP, rs -> new Climber(rs.getString(1), rs.getInt(2)), limit);
    }
}
