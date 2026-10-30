package dev.kage.server.database.account;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.account.domain.SecurityPinPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link SecurityPinPort} (ADR-115, V95). */
public final class PostgresSecurityPinAdapter implements SecurityPinPort {

    private static final String FIND = """
            SELECT pin_hash, pin_salt, failed_attempts, locked_until
              FROM account_security_pin WHERE account_id = ?
            """;

    private static final String UPSERT = """
            INSERT INTO account_security_pin (account_id, pin_hash, pin_salt, failed_attempts, locked_until, updated_at)
            VALUES (?, ?, ?, 0, NULL, ?)
            ON CONFLICT (account_id) DO UPDATE
               SET pin_hash = EXCLUDED.pin_hash, pin_salt = EXCLUDED.pin_salt, failed_attempts = 0,
                   locked_until = NULL, updated_at = EXCLUDED.updated_at
            """;

    private static final String ATTEMPTS =
            "UPDATE account_security_pin SET failed_attempts = ?, locked_until = ? WHERE account_id = ?";

    private static final String CLEAR = "DELETE FROM account_security_pin WHERE account_id = ?";

    private final DataSource dataSource;

    public PostgresSecurityPinAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<StoredPin> find(long accountId) {
        return JdbcOperations.queryOne(
                dataSource,
                FIND,
                rs -> {
                    Timestamp lockedUntil = rs.getTimestamp(4);
                    return new StoredPin(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getInt(3),
                            lockedUntil == null ? Instant.EPOCH : lockedUntil.toInstant());
                },
                accountId);
    }

    @Override
    public void save(long accountId, String hashHex, String saltHex, Instant at) {
        JdbcOperations.update(dataSource, UPSERT, accountId, hashHex, saltHex, Timestamp.from(at));
    }

    @Override
    public void recordAttempts(long accountId, int failedAttempts, Instant lockedUntil) {
        Timestamp until = Instant.EPOCH.equals(lockedUntil) ? null : Timestamp.from(lockedUntil);
        JdbcOperations.update(dataSource, ATTEMPTS, failedAttempts, until, accountId);
    }

    @Override
    public boolean clear(long accountId) {
        return JdbcOperations.update(dataSource, CLEAR, accountId) > 0;
    }
}
