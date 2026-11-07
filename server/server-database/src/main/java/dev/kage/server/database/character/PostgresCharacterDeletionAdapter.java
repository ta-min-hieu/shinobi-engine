package dev.kage.server.database.character;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.character.domain.CharacterDeletionPort;
import dev.kage.server.database.JdbcOperations;

/** ADR-205 — {@code character.deletion_requested_at}. */
public final class PostgresCharacterDeletionAdapter implements CharacterDeletionPort {

    private static final String REQUEST = """
            UPDATE character SET deletion_requested_at = COALESCE(deletion_requested_at, ?)
             WHERE id = ? AND account_id = ? AND (deletion_requested_at IS NULL OR deletion_requested_at >= ?)
            """;

    private static final String STATE = """
            SELECT deletion_requested_at FROM character WHERE id = ? AND account_id = ?
            """;

    private static final String CANCEL = """
            UPDATE character SET deletion_requested_at = NULL
             WHERE id = ? AND account_id = ? AND deletion_requested_at >= ?
            """;

    private static final String PENDING = """
            SELECT id, deletion_requested_at FROM character
             WHERE account_id = ? AND deletion_requested_at >= ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresCharacterDeletionAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean request(long accountId, long characterId, Instant now, Instant expiredBefore) {
        return JdbcOperations.update(
                        dataSource, REQUEST, Timestamp.from(now), characterId, accountId, Timestamp.from(expiredBefore))
                > 0;
    }

    @Override
    public State cancelIfPending(long accountId, long characterId, Instant expiredBefore) {
        java.util.Optional<java.util.Optional<Instant>> requested = JdbcOperations.queryOne(
                dataSource,
                STATE,
                rs -> java.util.Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant),
                characterId,
                accountId);
        if (requested.isEmpty() || requested.get().isEmpty()) {
            return State.NONE; // không phải nhân vật của tài khoản ⇒ đường chọn nhân vật tự báo không tìm thấy
        }
        if (requested.get().get().isBefore(expiredBefore)) {
            return State.EXPIRED;
        }
        JdbcOperations.update(dataSource, CANCEL, characterId, accountId, Timestamp.from(expiredBefore));
        return State.PENDING;
    }

    @Override
    public Map<Long, Instant> pendingOf(long accountId, Instant expiredBefore) {
        Map<Long, Instant> result = new HashMap<>();
        for (Map.Entry<Long, Instant> row : JdbcOperations.query(
                dataSource,
                PENDING,
                rs -> Map.entry(rs.getLong(1), rs.getTimestamp(2).toInstant()),
                accountId,
                Timestamp.from(expiredBefore))) {
            result.put(row.getKey(), row.getValue());
        }
        return result;
    }
}
