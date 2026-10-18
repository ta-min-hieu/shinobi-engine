package dev.kage.server.database.market;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import dev.kage.market.domain.MarketRecordPort;
import dev.kage.market.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/** Khoá chống trùng ({@code idempotency_key} dùng chung, ADR-025) + {@code audit_log} của phường thị. */
public final class PostgresMarketRecordAdapter implements MarketRecordPort {

    private static final String INSERT_KEY = "INSERT INTO idempotency_key (key, created_at) VALUES (?, ?)";

    private static final String INSERT_AUDIT =
            "INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)";

    @Override
    public void recordKey(Transaction tx, String idempotencyKey) {
        JdbcOperations.updateOrThrow(
                connection(tx),
                INSERT_KEY,
                () -> new DuplicateRequestException(null),
                UUID.fromString(idempotencyKey),
                Timestamp.from(Instant.now()));
    }

    @Override
    public void audit(Transaction tx, long characterId, String action, String detailJson) {
        JdbcOperations.update(
                connection(tx), INSERT_AUDIT, characterId, action, detailJson, Timestamp.from(Instant.now()));
    }

    private static Connection connection(Transaction tx) {
        return ((JdbcTransaction) tx).connection();
    }
}
