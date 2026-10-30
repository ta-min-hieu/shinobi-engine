package dev.kage.server.database.world;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.sql.DataSource;

import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.server.database.inventory.TemplateQuantityConsumer;
import dev.kage.world.domain.SpiritFieldPort;
import dev.kage.world.domain.SpiritFieldRules;

/** Hiện thực {@link SpiritFieldPort} (ADR-119, V99). */
public final class PostgresSpiritFieldAdapter implements SpiritFieldPort {

    private static final String SEEDS =
            "SELECT item_template_id, grow_minutes, yield_item_template_id, yield_quantity FROM field_seed ORDER BY 1";

    private static final String PLOTS = "SELECT plots FROM spirit_field WHERE character_id = ?";

    private static final String PLANTED = """
            SELECT p.plot_index, p.seed_item_template_id, s.yield_item_template_id, p.planted_at, p.ripe_at,
                   COALESCE(p.watered_by, 0)
              FROM spirit_field_plot p JOIN field_seed s ON s.item_template_id = p.seed_item_template_id
             WHERE p.character_id = ? ORDER BY p.plot_index
            """;

    private static final String PLOT_FOR_UPDATE = """
            SELECT p.ripe_at, p.watered_by IS NOT NULL, s.yield_item_template_id, s.yield_quantity,
                   p.seed_item_template_id
              FROM spirit_field_plot p JOIN field_seed s ON s.item_template_id = p.seed_item_template_id
             WHERE p.character_id = ? AND p.plot_index = ? FOR UPDATE OF p
            """;

    private static final String INSERT_PLOT = """
            INSERT INTO spirit_field_plot (character_id, plot_index, seed_item_template_id, planted_at, ripe_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String DELETE_PLOT = "DELETE FROM spirit_field_plot WHERE character_id = ? AND plot_index = ?";

    private static final String WATER = """
            UPDATE spirit_field_plot SET watered_by = ?
             WHERE character_id = ? AND plot_index = ? AND watered_by IS NULL
            """;

    private static final String UPSERT_FIELD = """
            INSERT INTO spirit_field (character_id) VALUES (?) ON CONFLICT (character_id) DO NOTHING
            """;

    private static final String PLOTS_FOR_UPDATE = "SELECT plots FROM spirit_field WHERE character_id = ? FOR UPDATE";

    private static final String ADD_PLOT = "UPDATE spirit_field SET plots = plots + 1 WHERE character_id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, now())
            """;

    private final DataSource dataSource;

    public PostgresSpiritFieldAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Seed> seeds() {
        return JdbcOperations.query(
                dataSource, SEEDS, rs -> new Seed(rs.getLong(1), rs.getInt(2), rs.getLong(3), rs.getInt(4)));
    }

    @Override
    public Field field(long characterId) {
        int plots = JdbcOperations.queryOne(dataSource, PLOTS, rs -> rs.getInt(1), characterId)
                .orElse(SpiritFieldRules.STARTING_PLOTS);
        List<Plot> planted = JdbcOperations.query(
                dataSource,
                PLANTED,
                rs -> new Plot(
                        rs.getInt(1),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getTimestamp(4).toInstant(),
                        rs.getTimestamp(5).toInstant(),
                        rs.getLong(6)),
                characterId);
        return new Field(plots, planted);
    }

    @Override
    public Outcome plant(long characterId, int plotIndex, long seedTemplateId, Instant now) {
        Optional<Seed> seed = seeds().stream()
                .filter(s -> s.itemTemplateId() == seedTemplateId)
                .findFirst();
        if (seed.isEmpty()) {
            return Outcome.UNKNOWN_SEED;
        }
        return run(connection -> {
            CharacterInventoryLock.acquire(connection, characterId);
            requireOwnPlot(connection, characterId, plotIndex);
            if (lockPlot(connection, characterId, plotIndex).isPresent()) {
                throw new Abort(Outcome.PLOT_OCCUPIED);
            }
            if (!TemplateQuantityConsumer.consume(connection, characterId, seedTemplateId, 1)) {
                throw new Abort(Outcome.NO_SEED);
            }
            JdbcOperations.update(
                    connection,
                    INSERT_PLOT,
                    characterId,
                    plotIndex,
                    seedTemplateId,
                    Timestamp.from(now),
                    Timestamp.from(
                            now.plus(java.time.Duration.ofMinutes(seed.get().growMinutes()))));
        });
    }

    @Override
    public Outcome harvest(
            long characterId, int plotIndex, Instant now, BiFunction<Integer, Boolean, Integer> yieldOf) {
        return run(connection -> {
            CharacterInventoryLock.acquire(connection, characterId);
            Object[] plot =
                    lockPlot(connection, characterId, plotIndex).orElseThrow(() -> new Abort(Outcome.PLOT_EMPTY));
            if (((Instant) plot[0]).isAfter(now)) {
                throw new Abort(Outcome.NOT_RIPE);
            }
            long yieldTemplate = (Long) plot[2];
            int quantity = yieldOf.apply((Integer) plot[3], (Boolean) plot[1]);
            if (!InventoryStackMerge.mergeIntoExistingStack(connection, characterId, yieldTemplate, quantity)
                    && !InventoryStackMerge.insertRespectingMaxStack(
                            connection, characterId, yieldTemplate, quantity)) {
                throw new Abort(Outcome.BAG_FULL);
            }
            JdbcOperations.update(connection, DELETE_PLOT, characterId, plotIndex);
            JdbcOperations.update(
                    connection,
                    AUDIT,
                    characterId,
                    "SPIRIT_FIELD_HARVEST",
                    "{\"plot\":" + plotIndex + ",\"itemTemplateId\":" + yieldTemplate + ",\"quantity\":" + quantity
                            + ",\"watered\":" + plot[1] + "}");
        });
    }

    @Override
    public Outcome water(long waterCharacterId, long ownerId, int plotIndex, Instant now) {
        return run(connection -> {
            Object[] plot = lockPlot(connection, ownerId, plotIndex).orElseThrow(() -> new Abort(Outcome.PLOT_EMPTY));
            if (!((Instant) plot[0]).isAfter(now)) {
                throw new Abort(Outcome.PLOT_EMPTY); // đã chín — không còn gì để tưới
            }
            if (JdbcOperations.update(connection, WATER, waterCharacterId, ownerId, plotIndex) == 0) {
                throw new Abort(Outcome.ALREADY_WATERED);
            }
        });
    }

    @Override
    public Outcome unlockPlot(long characterId) {
        return run(connection -> {
            CharacterInventoryLock.acquire(connection, characterId);
            JdbcOperations.update(connection, UPSERT_FIELD, characterId);
            int plots = JdbcOperations.queryOne(connection, PLOTS_FOR_UPDATE, rs -> rs.getInt(1), characterId)
                    .orElse(SpiritFieldRules.STARTING_PLOTS);
            if (plots >= SpiritFieldRules.MAX_PLOTS) {
                throw new Abort(Outcome.MAX_PLOTS);
            }
            if (!GoldLedger.deduct(
                    connection, characterId, SpiritFieldRules.unlockCost(plots), "gold.reason.spirit_field")) {
                throw new Abort(Outcome.INSUFFICIENT_GOLD);
            }
            JdbcOperations.update(connection, ADD_PLOT, characterId);
        });
    }

    private static void requireOwnPlot(Connection connection, long characterId, int plotIndex) {
        int plots = JdbcOperations.queryOne(connection, PLOTS, rs -> rs.getInt(1), characterId)
                .orElse(SpiritFieldRules.STARTING_PLOTS);
        if (plotIndex < 0 || plotIndex >= plots) {
            throw new Abort(Outcome.INVALID_PLOT);
        }
    }

    /** [ripeAt, watered, yieldTemplateId, yieldQuantity, seedTemplateId] của ô, khoá dòng. */
    private static Optional<Object[]> lockPlot(Connection connection, long characterId, int plotIndex) {
        return JdbcOperations.queryOne(
                connection,
                PLOT_FOR_UPDATE,
                rs -> new Object[] {
                    rs.getTimestamp(1).toInstant(), rs.getBoolean(2), rs.getLong(3), rs.getInt(4), rs.getLong(5)
                },
                characterId,
                plotIndex);
    }

    private Outcome run(java.util.function.Consumer<Connection> work) {
        try {
            JdbcTransactionRunner.run(dataSource, work);
            return Outcome.OK;
        } catch (Abort abort) {
            return abort.outcome;
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
