package dev.kage.server.database.shop;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.shop.domain.MerchantBuyOutcome;
import dev.kage.shop.domain.MerchantOffer;
import dev.kage.shop.domain.MerchantPort;
import dev.kage.shop.domain.MerchantStock;
import dev.kage.shop.domain.MerchantVisit;

/** Hiện thực {@link MerchantPort} (ADR-118, V98). */
public final class PostgresMerchantAdapter implements MerchantPort {

    private static final String REASON = "gold.reason.traveling_merchant";

    private static final String OFFERS =
            "SELECT item_template_id, price, quantity_per_visit, weight FROM traveling_merchant_offer ORDER BY 1";

    private static final String COUNT_STARTING =
            "SELECT COUNT(*) FROM traveling_merchant_visit WHERE starts_at >= ? AND starts_at < ?";

    private static final String INSERT_VISIT = """
            INSERT INTO traveling_merchant_visit (waypoint_code, starts_at, ends_at) VALUES (?, ?, ?) RETURNING id
            """;

    private static final String INSERT_STOCK = """
            INSERT INTO traveling_merchant_stock (visit_id, item_template_id, price, remaining) VALUES (?, ?, ?, ?)
            """;

    private static final String ACTIVE = """
            SELECT id, waypoint_code, starts_at, ends_at FROM traveling_merchant_visit
             WHERE starts_at <= ? AND ends_at > ? ORDER BY starts_at LIMIT 1
            """;

    private static final String CLAIM_UNANNOUNCED = """
            UPDATE traveling_merchant_visit SET announced = TRUE
             WHERE NOT announced AND starts_at <= ? AND ends_at > ?
            RETURNING id, waypoint_code, starts_at, ends_at
            """;

    private static final String STOCK = """
            SELECT item_template_id, price, remaining FROM traveling_merchant_stock WHERE visit_id = ? ORDER BY 1
            """;

    private static final String LOCK_STOCK = """
            SELECT s.price, s.remaining
              FROM traveling_merchant_stock s JOIN traveling_merchant_visit v ON v.id = s.visit_id
             WHERE s.visit_id = ? AND s.item_template_id = ? AND v.starts_at <= ? AND v.ends_at > ?
               FOR UPDATE OF s
            """;

    private static final String TAKE_ONE = """
            UPDATE traveling_merchant_stock SET remaining = remaining - 1 WHERE visit_id = ? AND item_template_id = ?
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresMerchantAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<MerchantOffer> offers() {
        return JdbcOperations.query(
                dataSource, OFFERS, rs -> new MerchantOffer(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getInt(4)));
    }

    @Override
    public int countVisitsStartingBetween(Instant from, Instant to) {
        return JdbcOperations.queryOne(
                        dataSource, COUNT_STARTING, rs -> rs.getInt(1), Timestamp.from(from), Timestamp.from(to))
                .orElse(0);
    }

    @Override
    public void insertVisit(String waypointCode, Instant startsAt, Instant endsAt, List<MerchantStock> stock) {
        JdbcTransactionRunner.run(dataSource, connection -> {
            long id = JdbcOperations.queryOne(
                            connection,
                            INSERT_VISIT,
                            rs -> rs.getLong(1),
                            waypointCode,
                            Timestamp.from(startsAt),
                            Timestamp.from(endsAt))
                    .orElseThrow();
            for (MerchantStock line : stock) {
                JdbcOperations.update(
                        connection, INSERT_STOCK, id, line.itemTemplateId(), line.price(), line.remaining());
            }
        });
    }

    @Override
    public Optional<MerchantVisit> active(Instant now) {
        return JdbcOperations.queryOne(
                        dataSource,
                        ACTIVE,
                        rs -> new MerchantVisit(
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getTimestamp(3).toInstant(),
                                rs.getTimestamp(4).toInstant(),
                                List.of()),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .map(this::withStock);
    }

    @Override
    public List<MerchantVisit> claimUnannouncedActive(Instant now) {
        List<MerchantVisit> claimed = JdbcOperations.query(
                dataSource,
                CLAIM_UNANNOUNCED,
                rs -> new MerchantVisit(
                        rs.getLong(1),
                        rs.getString(2),
                        rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4).toInstant(),
                        List.of()),
                Timestamp.from(now),
                Timestamp.from(now));
        List<MerchantVisit> withStock = new ArrayList<>();
        for (MerchantVisit visit : claimed) {
            withStock.add(withStock(visit));
        }
        return withStock;
    }

    private MerchantVisit withStock(MerchantVisit visit) {
        List<MerchantStock> stock = JdbcOperations.query(
                dataSource, STOCK, rs -> new MerchantStock(rs.getLong(1), rs.getLong(2), rs.getInt(3)), visit.id());
        return new MerchantVisit(visit.id(), visit.waypointCode(), visit.startsAt(), visit.endsAt(), stock);
    }

    @Override
    public MerchantBuyOutcome buy(long characterId, long visitId, long itemTemplateId, Instant now) {
        try {
            return JdbcTransactionRunner.call(dataSource, connection -> {
                CharacterInventoryLock.acquire(connection, characterId);
                Optional<long[]> line = JdbcOperations.queryOne(
                        connection,
                        LOCK_STOCK,
                        rs -> new long[] {rs.getLong(1), rs.getInt(2)},
                        visitId,
                        itemTemplateId,
                        Timestamp.from(now),
                        Timestamp.from(now));
                if (line.isEmpty()) {
                    return MerchantBuyOutcome.NOT_PRESENT;
                }
                if (line.get()[1] <= 0) {
                    return MerchantBuyOutcome.SOLD_OUT;
                }
                give(connection, characterId, itemTemplateId);
                if (!GoldLedger.deduct(connection, characterId, line.get()[0], REASON)) {
                    throw new Abort(MerchantBuyOutcome.INSUFFICIENT_GOLD);
                }
                JdbcOperations.update(connection, TAKE_ONE, visitId, itemTemplateId);
                JdbcOperations.update(
                        connection,
                        AUDIT,
                        characterId,
                        "TRAVELING_MERCHANT_BUY",
                        "{\"visitId\":" + visitId + ",\"itemTemplateId\":" + itemTemplateId + ",\"price\":"
                                + line.get()[0] + "}",
                        Timestamp.from(now));
                return MerchantBuyOutcome.OK;
            });
        } catch (Abort abort) {
            return abort.outcome;
        }
    }

    private static void give(Connection connection, long characterId, long itemTemplateId) {
        if (!InventoryStackMerge.mergeIntoExistingStack(connection, characterId, itemTemplateId, 1)
                && !InventoryStackMerge.insertRespectingMaxStack(connection, characterId, itemTemplateId, 1)) {
            throw new Abort(MerchantBuyOutcome.BAG_FULL);
        }
    }

    /** Từ chối giữa transaction — ném để rollback. */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient MerchantBuyOutcome outcome;

        Abort(MerchantBuyOutcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
