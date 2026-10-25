package dev.kage.server.database.achievement;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.achievement.domain.BestiaryPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link BestiaryPort} (ADR-108, V90). Cộng lần hạ là một upsert; "mở trang" là UPDATE có điều kiện
 * {@code unlocked_at IS NULL} — hai cú hạ chạm mốc cùng lúc thì chỉ một cú báo mở.
 */
public final class PostgresBestiaryAdapter implements BestiaryPort {

    private static final String ADD_KILL = """
            INSERT INTO character_bestiary (character_id, monster_code, kills)
            SELECT ?, monster_code, 1 FROM bestiary_entry WHERE monster_code = ?
            ON CONFLICT (character_id, monster_code) DO UPDATE SET kills = character_bestiary.kills + 1
            RETURNING kills
            """;

    private static final String UNLOCK = """
            UPDATE character_bestiary cb SET unlocked_at = ?
              FROM bestiary_entry e
             WHERE cb.character_id = ? AND cb.monster_code = ? AND e.monster_code = cb.monster_code
               AND cb.unlocked_at IS NULL AND cb.kills >= e.kills_required
            RETURNING e.monster_code, e.kills_required, e.bonus_stat, e.bonus_value, e.lore_key
            """;

    private static final String PROGRESS = """
            SELECT e.monster_code, e.kills_required, e.bonus_stat, e.bonus_value, e.lore_key,
                   COALESCE(cb.kills, 0), cb.unlocked_at IS NOT NULL
              FROM bestiary_entry e
              LEFT JOIN character_bestiary cb ON cb.monster_code = e.monster_code AND cb.character_id = ?
             ORDER BY e.sort_order, e.monster_code
            """;

    private static final String BONUS = """
            SELECT COALESCE(SUM(e.bonus_value) FILTER (WHERE e.bonus_stat = 'ATK'), 0),
                   COALESCE(SUM(e.bonus_value) FILTER (WHERE e.bonus_stat = 'DEF'), 0),
                   COALESCE(SUM(e.bonus_value) FILTER (WHERE e.bonus_stat = 'RES'), 0),
                   COALESCE(SUM(e.bonus_value) FILTER (WHERE e.bonus_stat = 'HP'), 0)
              FROM character_bestiary cb JOIN bestiary_entry e ON e.monster_code = cb.monster_code
             WHERE cb.character_id = ? AND cb.unlocked_at IS NOT NULL
            """;

    private final DataSource dataSource;

    public PostgresBestiaryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Entry> recordKill(long characterId, String monsterCode, Instant at) {
        return JdbcTransactionRunner.call(dataSource, connection -> recordIn(connection, characterId, monsterCode, at));
    }

    private static Optional<Entry> recordIn(Connection connection, long characterId, String monsterCode, Instant at) {
        if (JdbcOperations.queryOne(connection, ADD_KILL, rs -> rs.getInt(1), characterId, monsterCode)
                .isEmpty()) {
            return Optional.empty(); // loài không có trang (boss, quái lạ)
        }
        return JdbcOperations.queryOne(
                connection, UNLOCK, PostgresBestiaryAdapter::entry, Timestamp.from(at), characterId, monsterCode);
    }

    @Override
    public List<Progress> progressOf(long characterId) {
        return JdbcOperations.query(
                dataSource, PROGRESS, rs -> new Progress(entry(rs), rs.getInt(6), rs.getBoolean(7)), characterId);
    }

    @Override
    public Bonus bonusOf(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        BONUS,
                        rs -> new Bonus(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)),
                        characterId)
                .orElse(Bonus.NONE);
    }

    private static Entry entry(ResultSet rs) throws SQLException {
        return new Entry(
                rs.getString(1), rs.getInt(2), BonusStat.valueOf(rs.getString(3)), rs.getInt(4), rs.getString(5));
    }
}
