package dev.kage.server.database.pvp;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import javax.sql.DataSource;

import dev.kage.pvp.domain.HonorPort;
import dev.kage.pvp.domain.HonorRules;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;

/**
 * Hiện thực {@link HonorPort} (ADR-155, V126). Cộng điểm khoá dòng điểm-ngày ({@code FOR UPDATE}) để trần ngày đúng cả
 * khi hai trận của cùng một người chốt song song; đổi đồ mở đầu bằng khoá túi rồi khoá dòng điểm.
 */
public final class PostgresHonorAdapter implements HonorPort {

    private static final String ENSURE_HONOR =
            "INSERT INTO character_honor (character_id) VALUES (?) ON CONFLICT DO NOTHING";

    private static final String ENSURE_DAY =
            "INSERT INTO character_honor_day (character_id, day) VALUES (?, ?) ON CONFLICT DO NOTHING";

    private static final String LOCK_DAY =
            "SELECT gained FROM character_honor_day WHERE character_id = ? AND day = ? FOR UPDATE";

    private static final String ADD_DAY =
            "UPDATE character_honor_day SET gained = gained + ? WHERE character_id = ? AND day = ?";

    private static final String ADD_HONOR = "UPDATE character_honor SET earned = earned + ? WHERE character_id = ?";

    private static final String AVAILABLE =
            "SELECT COALESCE((SELECT earned - spent FROM character_honor WHERE character_id = ?), 0)";

    private static final String LOCK_AVAILABLE =
            "SELECT earned - spent FROM character_honor WHERE character_id = ? FOR UPDATE";

    private static final String SHOP = """
            SELECT s.item_template_id, s.price, s.quantity, s.weekly_limit, COALESCE(p.bought, 0)
              FROM honor_shop_item s
              LEFT JOIN character_honor_purchase p
                ON p.item_template_id = s.item_template_id AND p.character_id = ? AND p.week_start = ?
             ORDER BY s.sort_order
            """;

    private static final String SPEND = "UPDATE character_honor SET spent = spent + ? WHERE character_id = ?";

    private static final String COUNT_PURCHASE = """
            INSERT INTO character_honor_purchase (character_id, week_start, item_template_id, bought)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (character_id, week_start, item_template_id)
            DO UPDATE SET bought = character_honor_purchase.bought + 1
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Một lệnh đổi. */
    private record Purchase(long characterId, long itemTemplateId, LocalDate weekStart, Instant now) {}

    private final DataSource dataSource;

    public PostgresHonorAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int award(long characterId, int points, LocalDate day) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            Date date = Date.valueOf(day);
            JdbcOperations.update(connection, ENSURE_HONOR, characterId);
            JdbcOperations.update(connection, ENSURE_DAY, characterId, date);
            int gained = JdbcOperations.queryOne(connection, LOCK_DAY, rs -> rs.getInt(1), characterId, date)
                    .orElse(0);
            int granted = HonorRules.capped(gained, points);
            if (granted > 0) {
                JdbcOperations.update(connection, ADD_DAY, granted, characterId, date);
                JdbcOperations.update(connection, ADD_HONOR, granted, characterId);
            }
            return granted;
        });
    }

    @Override
    public Shop shop(long characterId, LocalDate weekStart) {
        return JdbcTransactionRunner.call(
                dataSource,
                connection -> new Shop(
                        JdbcOperations.queryOne(connection, AVAILABLE, rs -> rs.getLong(1), characterId)
                                .orElse(0L),
                        JdbcOperations.query(
                                connection,
                                SHOP,
                                rs -> new ShopItem(
                                        rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)),
                                characterId,
                                Date.valueOf(weekStart))));
    }

    @Override
    public Outcome buy(long characterId, long itemTemplateId, LocalDate weekStart, Instant now) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource,
                    connection -> buyIn(connection, new Purchase(characterId, itemTemplateId, weekStart, now)));
        } catch (Abort abort) {
            return abort.outcome;
        }
    }

    private static Outcome buyIn(Connection connection, Purchase purchase) {
        long characterId = purchase.characterId();
        Date week = Date.valueOf(purchase.weekStart());
        CharacterInventoryLock.acquire(connection, characterId);
        ShopItem item = JdbcOperations.query(
                        connection,
                        SHOP,
                        rs -> new ShopItem(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)),
                        characterId,
                        week)
                .stream()
                .filter(i -> i.itemTemplateId() == purchase.itemTemplateId())
                .findFirst()
                .orElseThrow(() -> new Abort(Outcome.UNKNOWN_ITEM));
        if (item.boughtThisWeek() >= item.weeklyLimit()) {
            throw new Abort(Outcome.WEEKLY_LIMIT);
        }
        long available = JdbcOperations.queryOne(connection, LOCK_AVAILABLE, rs -> rs.getLong(1), characterId)
                .orElse(0L);
        if (available < item.price()) {
            throw new Abort(Outcome.NOT_ENOUGH_HONOR);
        }
        JdbcOperations.update(connection, SPEND, item.price(), characterId);
        JdbcOperations.update(connection, COUNT_PURCHASE, characterId, week, item.itemTemplateId());
        if (!InventoryStackMerge.mergeIntoExistingStack(connection, characterId, item.itemTemplateId(), item.quantity())
                && !InventoryStackMerge.insertRespectingMaxStack(
                        connection, characterId, item.itemTemplateId(), item.quantity())) {
            throw new Abort(Outcome.BAG_FULL);
        }
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "HONOR_SHOP_BUY",
                "{\"itemTemplateId\":" + item.itemTemplateId() + ",\"quantity\":" + item.quantity() + ",\"price\":"
                        + item.price() + "}",
                Timestamp.from(purchase.now()));
        return Outcome.OK;
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
