package dev.kage.server.database.character;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.character.domain.RebirthPort;
import dev.kage.character.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link RebirthPort} (ADR-089) — cột {@code character.rebirth_count} có từ V2. */
public final class PostgresRebirthAdapter implements RebirthPort {

    private static final String LOCK = "SELECT level, rebirth_count FROM character WHERE id = ? FOR UPDATE";

    private static final String REBIRTH =
            "UPDATE character SET level = 1, exp = 0, rebirth_count = rebirth_count + 1 WHERE id = ?";

    private static final String COUNT = "SELECT rebirth_count FROM character WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresRebirthAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Progress> lock(Transaction tx, long characterId) {
        return JdbcOperations.queryOne(
                connection(tx), LOCK, rs -> new Progress(rs.getInt(1), rs.getInt(2)), characterId);
    }

    @Override
    public void rebirth(Transaction tx, long characterId) {
        Connection connection = connection(tx);
        JdbcOperations.update(connection, REBIRTH, characterId);
        JdbcOperations.update(connection, AUDIT, characterId, "REBIRTH", "{}", Timestamp.from(Instant.now()));
    }

    @Override
    public int rebirthCountOf(long characterId) {
        return JdbcOperations.queryOne(dataSource, COUNT, rs -> rs.getInt(1), characterId)
                .orElse(0);
    }

    private static Connection connection(Transaction tx) {
        return ((JdbcTransaction) tx).connection();
    }
}
