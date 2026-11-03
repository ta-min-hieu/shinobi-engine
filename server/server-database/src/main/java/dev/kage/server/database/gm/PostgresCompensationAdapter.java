package dev.kage.server.database.gm;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.gm.domain.CompensationPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link CompensationPort} (ADR-165, V133). */
public final class PostgresCompensationAdapter implements CompensationPort {

    private static final String INSERT = """
            INSERT INTO gm_compensation (code, gold, item_template_id, quantity, min_level, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (code) DO NOTHING
            """;

    private static final String EXISTING = """
            SELECT gold, COALESCE(item_template_id, 0), quantity, min_level, completed_at IS NOT NULL
              FROM gm_compensation WHERE code = ?
            """;

    private static final String RECIPIENTS = "SELECT id FROM character WHERE level >= ? ORDER BY id";

    private static final String FINISH = "UPDATE gm_compensation SET sent = sent + ?, completed_at = ? WHERE code = ?";

    /** Đợt đã có trong sổ. */
    private record Existing(CompensationPort.Campaign campaign, boolean done) {}

    private final DataSource dataSource;

    public PostgresCompensationAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Start begin(Campaign campaign, long createdBy, Instant at) {
        int inserted = JdbcOperations.update(
                dataSource,
                INSERT,
                campaign.code(),
                campaign.gold(),
                campaign.itemTemplateId() == 0L ? null : campaign.itemTemplateId(),
                campaign.quantity(),
                campaign.minLevel(),
                createdBy,
                Timestamp.from(at));
        if (inserted > 0) {
            return Start.NEW;
        }
        Optional<Existing> existing = JdbcOperations.queryOne(
                dataSource,
                EXISTING,
                rs -> new Existing(
                        new Campaign(campaign.code(), rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getInt(4)),
                        rs.getBoolean(5)),
                campaign.code());
        if (existing.isEmpty() || !existing.get().campaign().equals(campaign)) {
            return Start.CONFLICT;
        }
        return existing.get().done() ? Start.DONE : Start.RESUME;
    }

    @Override
    public List<Long> recipients(int minLevel) {
        return JdbcOperations.query(dataSource, RECIPIENTS, rs -> rs.getLong(1), minLevel);
    }

    @Override
    public void finish(String code, int sent, Instant at) {
        JdbcOperations.update(dataSource, FINISH, sent, Timestamp.from(at), code);
    }
}
