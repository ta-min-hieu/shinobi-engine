package dev.kage.server.database.monster;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.monster.domain.WorldBossPort;
import dev.kage.monster.domain.WorldBossReward;
import dev.kage.monster.domain.WorldBossSchedule;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link WorldBossPort} (ADR-081, V78). */
public final class PostgresWorldBossAdapter implements WorldBossPort {

    private static final String SCHEDULES =
            "SELECT monster_code, spawn_x, spawn_y, interval_minutes FROM world_boss_schedule ORDER BY monster_code";

    private static final String REWARDS = """
            SELECT rank_from, rank_to, gold, COALESCE(item_template_id, 0), quantity
              FROM world_boss_reward
             WHERE monster_code = ?
             ORDER BY rank_from
            """;

    private static final String ADD_DAMAGE = """
            INSERT INTO world_boss_damage (character_id, total_damage) VALUES (?, ?)
            ON CONFLICT (character_id)
            DO UPDATE SET total_damage = world_boss_damage.total_damage + EXCLUDED.total_damage
            """;

    private static final String TOP = """
            SELECT character_id, total_damage
              FROM world_boss_damage
             WHERE total_damage > 0
             ORDER BY total_damage DESC, character_id
             LIMIT ?
            """;

    private final DataSource dataSource;

    public PostgresWorldBossAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<WorldBossSchedule> schedules() {
        return JdbcOperations.query(
                dataSource,
                SCHEDULES,
                rs -> new WorldBossSchedule(rs.getString(1), rs.getFloat(2), rs.getFloat(3), rs.getInt(4)));
    }

    @Override
    public List<WorldBossReward> rewards(String monsterCode) {
        return JdbcOperations.query(
                dataSource,
                REWARDS,
                rs -> new WorldBossReward(rs.getInt(1), rs.getInt(2), rs.getLong(3), rs.getLong(4), rs.getInt(5)),
                monsterCode);
    }

    @Override
    public void addDamage(Map<Long, Long> damageByCharacter) {
        if (damageByCharacter.isEmpty()) {
            return;
        }
        // Thứ tự id cố định: hai boss chết cùng lúc ghi cùng tập nhân vật theo cùng thứ tự — không deadlock.
        List<Map.Entry<Long, Long>> rows = damageByCharacter.entrySet().stream()
                .filter(row -> row.getValue() > 0)
                .sorted(Map.Entry.comparingByKey())
                .toList();
        JdbcTransactionRunner.run(dataSource, connection -> {
            for (Map.Entry<Long, Long> row : rows) {
                JdbcOperations.update(connection, ADD_DAMAGE, row.getKey(), row.getValue());
            }
        });
    }

    @Override
    public List<DamageRank> topDamage(int limit) {
        return JdbcOperations.query(dataSource, TOP, rs -> new DamageRank(rs.getLong(1), rs.getLong(2)), limit);
    }
}
