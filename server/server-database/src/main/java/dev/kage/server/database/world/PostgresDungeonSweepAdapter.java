package dev.kage.server.database.world;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import javax.sql.DataSource;

import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.world.domain.DungeonSweepPort;

/**
 * Hiện thực {@link DungeonSweepPort} (ADR-162, V130). Khoá dòng lượt ngày ({@code FOR UPDATE}) như lệnh vào Bí Cảnh
 * thật (ADR-099), nên quét và vào song song không vượt trần. Hỏng bước nào thì ném {@link Abort} để rollback.
 */
public final class PostgresDungeonSweepAdapter implements DungeonSweepPort {

    private static final String GOLD_REASON = "gold.reason.dungeon_sweep";

    private static final String RULE =
            "SELECT gold_fee, exp_reward, drop_table_code FROM dungeon_sweep_rule WHERE dungeon_code = ?";

    private static final String CLEARED =
            "SELECT COUNT(*) FROM dungeon_first_clear WHERE character_id = ? AND dungeon_code = ?";

    private static final String ENSURE = """
            INSERT INTO dungeon_entry (character_id, dungeon_code, day) VALUES (?, ?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String LOCK = """
            SELECT entries FROM dungeon_entry WHERE character_id = ? AND dungeon_code = ? AND day = ? FOR UPDATE
            """;

    private static final String CONSUME = """
            UPDATE dungeon_entry SET entries = entries + 1 WHERE character_id = ? AND dungeon_code = ? AND day = ?
            """;

    private static final String ADD_EXP = "UPDATE character SET exp = exp + ? WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Luật quét một Bí Cảnh. */
    private record Rule(long goldFee, long expReward, String dropTableCode) {}

    /** Một lệnh quét. */
    private record Sweep(long characterId, String dungeonCode, Date day, int maxEntries) {}

    private final DataSource dataSource;

    public PostgresDungeonSweepAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Result sweep(long characterId, String dungeonCode, LocalDate day, int maxEntries) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource,
                    connection ->
                            sweepIn(connection, new Sweep(characterId, dungeonCode, Date.valueOf(day), maxEntries)));
        } catch (Abort abort) {
            return Result.refused(abort.outcome, abort.entriesUsed);
        }
    }

    private static Result sweepIn(Connection connection, Sweep sweep) {
        long characterId = sweep.characterId();
        String code = sweep.dungeonCode();
        Rule rule = JdbcOperations.queryOne(
                        connection, RULE, rs -> new Rule(rs.getLong(1), rs.getLong(2), rs.getString(3)), code)
                .orElseThrow(() -> new Abort(Outcome.UNKNOWN_DUNGEON, 0));
        JdbcOperations.update(connection, ENSURE, characterId, code, sweep.day());
        int used = JdbcOperations.queryOne(connection, LOCK, rs -> rs.getInt(1), characterId, code, sweep.day())
                .orElse(0);
        if (JdbcOperations.queryOne(connection, CLEARED, rs -> rs.getInt(1), characterId, code)
                        .orElse(0)
                == 0) {
            throw new Abort(Outcome.NOT_CLEARED, used);
        }
        if (used >= sweep.maxEntries()) {
            throw new Abort(Outcome.DAILY_LIMIT_REACHED, used);
        }
        if (!GoldLedger.deduct(connection, characterId, rule.goldFee(), GOLD_REASON)) {
            throw new Abort(Outcome.NOT_ENOUGH_GOLD, used);
        }
        JdbcOperations.update(connection, CONSUME, characterId, code, sweep.day());
        JdbcOperations.update(connection, ADD_EXP, rule.expReward(), characterId);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "DUNGEON_SWEPT",
                "{\"dungeon\":\"" + code + "\",\"gold\":" + rule.goldFee() + ",\"exp\":" + rule.expReward() + "}",
                Timestamp.from(Instant.now()));
        return new Result(Outcome.OK, rule.expReward(), rule.dropTableCode(), used + 1);
    }

    /** Từ chối giữa transaction — ném để rollback. */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Outcome outcome;
        private final int entriesUsed;

        Abort(Outcome outcome, int entriesUsed) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
            this.entriesUsed = entriesUsed;
        }
    }
}
