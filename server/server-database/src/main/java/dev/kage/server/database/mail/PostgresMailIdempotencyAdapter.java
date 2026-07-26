package dev.kage.server.database.mail;

import dev.kage.mail.domain.IdempotencyKeyReusedException;
import dev.kage.mail.domain.IdempotencyPort;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Hiện thực {@link IdempotencyPort} trên PostgreSQL — bảng {@code idempotency_key} DÙNG CHUNG
 * (V2, ADR-025), bản sao ý tưởng {@code PostgresCharacterAdapter.insertIdempotencyKey}/
 * {@code PostgresShopIdempotencyAdapter}.
 */
public final class PostgresMailIdempotencyAdapter implements IdempotencyPort {

    private static final String INSERT_IDEMPOTENCY_KEY = """
            INSERT INTO idempotency_key (key, created_at) VALUES (?, ?)
            """;

    @Override
    public void recordOrThrow(Transaction tx, String idempotencyKey) {
        Connection connection = ((JdbcTransaction) tx).connection();
        JdbcOperations.updateOrThrow(
                connection,
                INSERT_IDEMPOTENCY_KEY,
                () -> new IdempotencyKeyReusedException(idempotencyKey),
                UUID.fromString(idempotencyKey),
                Timestamp.from(Instant.now()));
    }
}
