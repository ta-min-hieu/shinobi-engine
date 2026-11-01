package dev.kage.server.database.world;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import dev.kage.server.database.GoldCapExceededException;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.world.domain.KillingIntentPort;
import dev.kage.world.domain.KillingIntentRules;

/** Hiện thực {@link KillingIntentPort} (ADR-142, V115). Phân rã tính ở Java theo {@link KillingIntentRules}. */
public final class PostgresKillingIntentAdapter implements KillingIntentPort {

    private static final String ROW = "SELECT points, updated_at FROM character_killing_intent WHERE character_id = ?";

    private static final String ROW_FOR_UPDATE = ROW + " FOR UPDATE";

    private static final String UPSERT = """
            INSERT INTO character_killing_intent (character_id, points, updated_at) VALUES (?, ?, ?)
            ON CONFLICT (character_id) DO UPDATE SET points = EXCLUDED.points, updated_at = EXCLUDED.updated_at
            """;

    /** Ứng viên Ma Đầu: còn đủ điểm thô — lọc phân rã chính xác ở Java. */
    private static final String CANDIDATES =
            "SELECT character_id, points, updated_at FROM character_killing_intent WHERE points >= ?";

    private static final String GOLD_FOR_UPDATE = "SELECT gold FROM character_stat WHERE character_id = ? FOR UPDATE";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, now())
            """;

    private static final String REASON_DROP = "gold.reason.outlaw_drop";
    private static final String REASON_BOUNTY = "gold.reason.outlaw_bounty";

    private final DataSource dataSource;

    public PostgresKillingIntentAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Điểm thô + mốc của một dòng. */
    private record Stored(int points, Instant updatedAt) {
        int effective(Instant now) {
            return KillingIntentRules.effective(points, updatedAt, now);
        }
    }

    @Override
    public int points(long characterId, Instant now) {
        return JdbcOperations.queryOne(dataSource, ROW, PostgresKillingIntentAdapter::stored, characterId)
                .map(row -> row.effective(now))
                .orElse(0);
    }

    @Override
    public int recordKill(long killerId, Instant now) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            int current =
                    locked(connection, killerId).map(row -> row.effective(now)).orElse(0);
            int next = KillingIntentRules.afterKill(current);
            JdbcOperations.update(connection, UPSERT, killerId, next, Timestamp.from(now));
            return next;
        });
    }

    @Override
    public Set<Long> outlaws(Instant now) {
        Set<Long> outlaws = new HashSet<>();
        JdbcOperations.query(
                        dataSource,
                        CANDIDATES,
                        rs -> new Object[] {rs.getLong(1), stored(rs.getInt(2), rs.getTimestamp(3))},
                        KillingIntentRules.OUTLAW_POINTS)
                .forEach(row -> {
                    if (KillingIntentRules.isOutlaw(((Stored) row[1]).effective(now))) {
                        outlaws.add((Long) row[0]);
                    }
                });
        return Set.copyOf(outlaws);
    }

    @Override
    public long dropGold(long victimId, long killerId, Instant now) {
        try {
            return dropWithin(victimId, killerId, now);
        } catch (GoldCapExceededException capped) {
            return 0L; // người hạ đã chạm trần vàng — cả lượt rơi bị huỷ, Ma Đầu giữ vàng lần này
        }
    }

    private long dropWithin(long victimId, long killerId, Instant now) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            boolean outlaw = locked(connection, victimId)
                    .map(row -> KillingIntentRules.isOutlaw(row.effective(now)))
                    .orElse(false);
            if (!outlaw) {
                return 0L;
            }
            long gold = JdbcOperations.queryOne(connection, GOLD_FOR_UPDATE, rs -> rs.getLong(1), victimId)
                    .orElse(0L);
            long drop = KillingIntentRules.goldDrop(gold);
            if (drop <= 0L || !GoldLedger.deduct(connection, victimId, drop, REASON_DROP)) {
                return 0L;
            }
            if (killerId > 0L) {
                GoldLedger.credit(connection, killerId, drop, REASON_BOUNTY);
            }
            JdbcOperations.update(
                    connection,
                    AUDIT,
                    victimId,
                    "OUTLAW_GOLD_DROP",
                    "{\"amount\":" + drop + ",\"killer\":" + killerId + "}");
            return drop;
        });
    }

    private static Optional<Stored> locked(Connection connection, long characterId) {
        return JdbcOperations.queryOne(connection, ROW_FOR_UPDATE, PostgresKillingIntentAdapter::stored, characterId);
    }

    private static Stored stored(ResultSet rs) throws SQLException {
        return stored(rs.getInt(1), rs.getTimestamp(2));
    }

    private static Stored stored(int points, Timestamp updatedAt) {
        return new Stored(points, updatedAt.toInstant());
    }
}
