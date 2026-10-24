package dev.kage.server.database.character;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.character.domain.SeclusionPolicy;
import dev.kage.character.domain.SeclusionPort;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link SeclusionPort} (ADR-095, V86). Cộng EXP thẳng vào {@code character.exp} trong cùng giao dịch xoá quỹ
 * — cùng đường của thưởng quest ({@code PostgresQuestExpAdapter}); gateway đồng bộ cấp sau đó.
 */
public final class PostgresSeclusionAdapter implements SeclusionPort {

    private static final String GOLD_REASON = "gold.reason.seclusion";

    private static final String LOCK_LOGOUT = "SELECT last_logout_at FROM character WHERE id = ? FOR UPDATE";

    private static final String ADD_TO_BANK = """
            INSERT INTO seclusion_bank (character_id, banked_minutes) VALUES (?, ?)
            ON CONFLICT (character_id) DO UPDATE
               SET banked_minutes = LEAST(?, seclusion_bank.banked_minutes + EXCLUDED.banked_minutes),
                   updated_at = now()
            RETURNING banked_minutes
            """;

    private static final String CLEAR_LOGOUT = "UPDATE character SET last_logout_at = NULL WHERE id = ?";

    private static final String MARK_LOGOUT = "UPDATE character SET last_logout_at = ? WHERE id = ?";

    private static final String BANKED = "SELECT banked_minutes FROM seclusion_bank WHERE character_id = ?";

    private static final String LOCK_BANK =
            "SELECT banked_minutes FROM seclusion_bank WHERE character_id = ? FOR UPDATE";

    private static final String LOCK_LEVEL = "SELECT level FROM character WHERE id = ? FOR UPDATE";

    private static final String ADD_EXP = "UPDATE character SET exp = exp + ? WHERE id = ?";

    private static final String EMPTY_BANK =
            "UPDATE seclusion_bank SET banked_minutes = 0, updated_at = now() WHERE character_id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresSeclusionAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int bankOfflineTime(long characterId, Instant now) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            Optional<Instant> loggedOut = JdbcOperations.queryOne(
                            connection,
                            LOCK_LOGOUT,
                            rs -> Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant),
                            characterId)
                    .flatMap(value -> value);
            if (loggedOut.isEmpty()) {
                return bankedIn(connection, characterId);
            }
            int minutes = SeclusionPolicy.offlineMinutes(loggedOut.get(), now);
            JdbcOperations.update(connection, CLEAR_LOGOUT, characterId);
            return JdbcOperations.queryOne(
                            connection,
                            ADD_TO_BANK,
                            rs -> rs.getInt(1),
                            characterId,
                            minutes,
                            SeclusionPolicy.MAX_BANKED_MINUTES)
                    .orElse(0);
        });
    }

    @Override
    public void markLoggedOut(long characterId, Instant now) {
        JdbcOperations.update(dataSource, MARK_LOGOUT, Timestamp.from(now), characterId);
    }

    @Override
    public int bankedMinutes(long characterId) {
        return JdbcOperations.queryOne(dataSource, BANKED, rs -> rs.getInt(1), characterId)
                .orElse(0);
    }

    private static int bankedIn(Connection connection, long characterId) {
        return JdbcOperations.queryOne(connection, BANKED, rs -> rs.getInt(1), characterId)
                .orElse(0);
    }

    @Override
    public Claim claim(long characterId, boolean doubled) {
        return JdbcTransactionRunner.call(dataSource, connection -> claimIn(connection, characterId, doubled));
    }

    private static Claim claimIn(Connection connection, long characterId, boolean doubled) {
        int minutes = JdbcOperations.queryOne(connection, LOCK_BANK, rs -> rs.getInt(1), characterId)
                .orElse(0);
        if (minutes <= 0) {
            return new Claim(Outcome.NOTHING_BANKED, 0L);
        }
        int level = JdbcOperations.queryOne(connection, LOCK_LEVEL, rs -> rs.getInt(1), characterId)
                .orElseThrow();
        long exp = SeclusionPolicy.expFor(minutes, level) * (doubled ? 2L : 1L);
        long cost = doubled ? SeclusionPolicy.doubleCost(level) : 0L;
        if (cost > 0L && !GoldLedger.deduct(connection, characterId, cost, GOLD_REASON)) {
            return new Claim(Outcome.INSUFFICIENT_GOLD, 0L); // chưa ghi gì — không cần rollback
        }
        JdbcOperations.update(connection, ADD_EXP, exp, characterId);
        JdbcOperations.update(connection, EMPTY_BANK, characterId);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "SECLUSION_CLAIMED",
                "{\"minutes\":" + minutes + ",\"exp\":" + exp + ",\"goldCost\":" + cost + "}",
                Timestamp.from(Instant.now()));
        return new Claim(Outcome.OK, exp);
    }
}
