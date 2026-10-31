package dev.kage.server.database.character;

import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import dev.kage.character.domain.TribulationPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link TribulationPort} (ADR-126, V106). */
public final class PostgresTribulationAdapter implements TribulationPort {

    private static final String PASSED = "SELECT tribulations_passed FROM character WHERE id = ?";
    private static final String ADVANCE = """
            UPDATE character SET tribulations_passed = tribulations_passed + 1
             WHERE id = ? AND tribulations_passed = ? AND tribulations_passed < 5
            """;
    private static final String PENALIZE = "UPDATE character SET exp = GREATEST(?, exp - ?) WHERE id = ?";
    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresTribulationAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int passed(long characterId) {
        return JdbcOperations.queryOne(dataSource, PASSED, rs -> rs.getInt(1), characterId)
                .orElse(0);
    }

    @Override
    public boolean advance(long characterId, int expectedPassed) {
        boolean advanced = JdbcOperations.update(dataSource, ADVANCE, characterId, expectedPassed) == 1;
        if (advanced) {
            audit(characterId, "TRIBULATION_PASSED", "{\"passed\":" + (expectedPassed + 1) + "}");
        }
        return advanced;
    }

    @Override
    public void penalize(long characterId, long amount, long floorExp) {
        JdbcOperations.update(dataSource, PENALIZE, floorExp, amount, characterId);
        audit(characterId, "TRIBULATION_FAILED", "{\"expLost\":" + amount + ",\"floorExp\":" + floorExp + "}");
    }

    private void audit(long characterId, String action, String detail) {
        JdbcOperations.update(dataSource, AUDIT, characterId, action, detail, Timestamp.from(Instant.now()));
    }
}
