package dev.kage.server.database.character;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.character.domain.SkillRankPort;
import dev.kage.character.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link SkillRankPort} (ADR-071, V72). */
public final class PostgresSkillRankAdapter implements SkillRankPort {

    private static final String RANKS = "SELECT skill_code, rank FROM character_skill_rank WHERE character_id = ?";
    private static final String LOCK_CHARACTER = "SELECT id FROM character WHERE id = ? FOR UPDATE";
    private static final String UPSERT = """
            INSERT INTO character_skill_rank (character_id, skill_code, rank) VALUES (?, ?, ?)
            ON CONFLICT (character_id, skill_code) DO UPDATE SET rank = EXCLUDED.rank
            """;

    private final DataSource dataSource;

    public PostgresSkillRankAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<String, Integer> ranksOf(long characterId) {
        Map<String, Integer> ranks = new HashMap<>();
        JdbcOperations.query(dataSource, RANKS, rs -> Map.entry(rs.getString(1), rs.getInt(2)), characterId)
                .forEach(row -> ranks.put(row.getKey(), row.getValue()));
        return ranks;
    }

    @Override
    public Map<String, Integer> lockRanks(Transaction tx, long characterId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        JdbcOperations.lock(connection, LOCK_CHARACTER, characterId);
        Map<String, Integer> ranks = new HashMap<>();
        JdbcOperations.query(connection, RANKS, rs -> Map.entry(rs.getString(1), rs.getInt(2)), characterId)
                .forEach(row -> ranks.put(row.getKey(), row.getValue()));
        return ranks;
    }

    @Override
    public void setRank(Transaction tx, long characterId, String skillCode, int rank) {
        JdbcOperations.update(((JdbcTransaction) tx).connection(), UPSERT, characterId, skillCode, rank);
    }
}
