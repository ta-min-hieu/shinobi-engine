package dev.kage.server.database.gm;

import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import dev.kage.gm.domain.GmAuditEntry;
import dev.kage.gm.domain.GmAuditPort;
import dev.kage.gm.domain.GmOutcome;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link GmAuditPort} (ADR-052 D5) — INSERT dòng PENDING, rồi UPDATE đúng một lần có điều kiện
 * {@code outcome='PENDING'}. Không có câu DELETE nào (SECURITY.md §9).
 */
public final class PostgresGmAuditAdapter implements GmAuditPort {

    private static final String OPEN = """
            INSERT INTO gm_audit_log (actor_account_id, actor_character_id, actor_role, command, command_line,
                                      target_character_id, outcome, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
            RETURNING id
            """;

    private static final String CLOSE = """
            UPDATE gm_audit_log SET outcome = ?, outcome_detail = ?, completed_at = ?
             WHERE id = ? AND outcome = 'PENDING'
            """;

    private final DataSource dataSource;

    public PostgresGmAuditAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public long open(GmAuditEntry entry) {
        Long target = entry.targetCharacterId().isPresent()
                ? entry.targetCharacterId().getAsLong()
                : null;
        return JdbcOperations.queryOne(
                        dataSource,
                        OPEN,
                        rs -> rs.getLong("id"),
                        entry.actorAccountId(),
                        entry.actorCharacterId(),
                        entry.actorRole().name(),
                        entry.command(),
                        entry.commandLine(),
                        target,
                        Timestamp.from(Instant.now()))
                .orElseThrow(() -> new IllegalStateException("INSERT gm_audit_log không trả id"));
    }

    @Override
    public void close(long auditId, GmOutcome outcome, String detailJson) {
        JdbcOperations.update(dataSource, CLOSE, outcome.name(), detailJson, Timestamp.from(Instant.now()), auditId);
    }
}
