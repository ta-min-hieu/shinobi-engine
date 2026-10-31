package dev.kage.server.database.world;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;

import javax.sql.DataSource;

import dev.kage.server.database.GoldCapExceededException;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.world.domain.CavePort;
import dev.kage.world.domain.CaveRules;

/** Hiện thực {@link CavePort} (ADR-129, V108). */
public final class PostgresCaveAdapter implements CavePort {

    private static final String CATALOG = """
            SELECT code, name_key, gold_cost, bonus_percent FROM cave_decoration_template ORDER BY sort_order
            """;

    private static final String ENSURE = """
            INSERT INTO cave_dwelling (character_id, last_collected_at) VALUES (?, ?)
            ON CONFLICT (character_id) DO NOTHING
            """;

    private static final String CAVE = "SELECT vein_level, last_collected_at FROM cave_dwelling WHERE character_id = ?";

    private static final String CAVE_FOR_UPDATE = CAVE + " FOR UPDATE";

    private static final String DECORATIONS = """
            SELECT d.decoration_code FROM cave_decoration d
              JOIN cave_decoration_template t ON t.code = d.decoration_code
             WHERE d.character_id = ? ORDER BY t.sort_order
            """;

    private static final String BONUS = """
            SELECT COALESCE(SUM(t.bonus_percent), 0) FROM cave_decoration d
              JOIN cave_decoration_template t ON t.code = d.decoration_code
             WHERE d.character_id = ?
            """;

    private static final String SET_COLLECTED = "UPDATE cave_dwelling SET last_collected_at = ? WHERE character_id = ?";

    private static final String RAISE_LEVEL =
            "UPDATE cave_dwelling SET vein_level = vein_level + 1 WHERE character_id = ?";

    private static final String PLACE = """
            INSERT INTO cave_decoration (character_id, decoration_code) VALUES (?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, now())
            """;

    private static final String REASON_VEIN = "gold.reason.cave_vein";
    private static final String REASON_UPGRADE = "gold.reason.cave_upgrade";
    private static final String REASON_DECORATION = "gold.reason.cave_decoration";

    private final DataSource dataSource;

    public PostgresCaveAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Decoration> catalog() {
        return JdbcOperations.query(
                dataSource,
                CATALOG,
                rs -> new Decoration(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getInt(4)));
    }

    @Override
    public Cave cave(long characterId, Instant now) {
        JdbcOperations.update(dataSource, ENSURE, characterId, Timestamp.from(now));
        Object[] row = JdbcOperations.queryOne(
                        dataSource,
                        CAVE,
                        rs -> new Object[] {rs.getInt(1), rs.getTimestamp(2).toInstant()},
                        characterId)
                .orElseThrow();
        List<String> decorations = JdbcOperations.query(dataSource, DECORATIONS, rs -> rs.getString(1), characterId);
        return new Cave((Integer) row[0], (Instant) row[1], decorations);
    }

    @Override
    public Result collect(long characterId, Instant now) {
        return run(connection -> {
            long collected = collectWithin(connection, characterId, now);
            if (collected == 0L) {
                throw new Abort(Outcome.NOTHING_TO_COLLECT);
            }
            return collected;
        });
    }

    @Override
    public Result upgrade(long characterId, Instant now) {
        return run(connection -> {
            int level = lockCave(connection, characterId, now).level();
            if (level >= CaveRules.MAX_VEIN_LEVEL) {
                throw new Abort(Outcome.MAX_LEVEL);
            }
            long collected = collectWithin(connection, characterId, now);
            if (!GoldLedger.deduct(connection, characterId, CaveRules.upgradeCost(level), REASON_UPGRADE)) {
                throw new Abort(Outcome.INSUFFICIENT_GOLD);
            }
            JdbcOperations.update(connection, RAISE_LEVEL, characterId);
            audit(connection, characterId, "CAVE_UPGRADE", "{\"toLevel\":" + (level + 1) + "}");
            return collected;
        });
    }

    @Override
    public Result decorate(long characterId, String code, Instant now) {
        Optional<Decoration> decoration =
                catalog().stream().filter(d -> d.code().equals(code)).findFirst();
        if (decoration.isEmpty()) {
            return new Result(Outcome.UNKNOWN_DECORATION, 0L);
        }
        long cost = decoration.get().goldCost();
        return run(connection -> {
            lockCave(connection, characterId, now);
            long collected = collectWithin(connection, characterId, now);
            if (JdbcOperations.update(connection, PLACE, characterId, code) == 0) {
                throw new Abort(Outcome.ALREADY_PLACED);
            }
            if (!GoldLedger.deduct(connection, characterId, cost, REASON_DECORATION)) {
                throw new Abort(Outcome.INSUFFICIENT_GOLD);
            }
            audit(connection, characterId, "CAVE_DECORATION", "{\"code\":\"" + code + "\"}");
            return collected;
        });
    }

    /** Dòng động phủ đã khoá. */
    private record Locked(int level, Instant lastCollectedAt) {}

    private static Locked lockCave(Connection connection, long characterId, Instant now) {
        JdbcOperations.update(connection, ENSURE, characterId, Timestamp.from(now));
        return JdbcOperations.queryOne(
                        connection,
                        CAVE_FOR_UPDATE,
                        rs -> new Locked(rs.getInt(1), rs.getTimestamp(2).toInstant()),
                        characterId)
                .orElseThrow();
    }

    /** Thu kho theo luật HIỆN TẠI (cấp + bày trí trước lượt đổi) và mốc tích mới = {@code now}. */
    private static long collectWithin(Connection connection, long characterId, Instant now) {
        Locked cave = lockCave(connection, characterId, now);
        int bonus = JdbcOperations.queryOne(connection, BONUS, rs -> rs.getInt(1), characterId)
                .orElse(0);
        long amount = CaveRules.accrued(cave.level(), bonus, cave.lastCollectedAt(), now);
        if (amount > 0L) {
            GoldLedger.credit(connection, characterId, amount, REASON_VEIN);
            audit(connection, characterId, "CAVE_COLLECT", "{\"amount\":" + amount + "}");
        }
        JdbcOperations.update(connection, SET_COLLECTED, Timestamp.from(now), characterId);
        return amount;
    }

    private static void audit(Connection connection, long characterId, String action, String detail) {
        JdbcOperations.update(connection, AUDIT, characterId, action, detail);
    }

    private Result run(ToLongFunction<Connection> work) {
        long[] collected = new long[1];
        try {
            JdbcTransactionRunner.run(dataSource, connection -> {
                collected[0] = work.applyAsLong(connection);
            });
            return new Result(Outcome.OK, collected[0]);
        } catch (Abort abort) {
            return new Result(abort.outcome, 0L);
        } catch (GoldCapExceededException capped) {
            return new Result(Outcome.GOLD_CAP, 0L);
        }
    }

    /** Từ chối giữa transaction — ném để rollback. */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Outcome outcome;

        Abort(Outcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
