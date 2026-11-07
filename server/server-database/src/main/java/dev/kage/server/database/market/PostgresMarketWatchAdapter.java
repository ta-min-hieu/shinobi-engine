package dev.kage.server.database.market;

import java.sql.Timestamp;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.market.domain.MarketWatchPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** ADR-196 — {@code market_watch}; trần số dòng kiểm sau khi khoá dòng nhân vật (hai lệnh đồng thời không cùng lọt). */
public final class PostgresMarketWatchAdapter implements MarketWatchPort {

    private static final String LOCK_CHARACTER = "SELECT id FROM character WHERE id = ? FOR UPDATE";
    private static final String TEMPLATE_EXISTS = "SELECT 1 FROM item_template WHERE id = ?";
    private static final String EXISTS = "SELECT 1 FROM market_watch WHERE character_id = ? AND item_template_id = ?";
    private static final String COUNT = "SELECT COUNT(*) FROM market_watch WHERE character_id = ?";
    private static final String UPSERT = """
            INSERT INTO market_watch (character_id, item_template_id, max_unit_price, created_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (character_id, item_template_id) DO UPDATE SET max_unit_price = EXCLUDED.max_unit_price
            """;
    private static final String REMOVE = "DELETE FROM market_watch WHERE character_id = ? AND item_template_id = ?";
    private static final String LIST = """
            SELECT item_template_id, max_unit_price FROM market_watch
             WHERE character_id = ? ORDER BY created_at DESC, item_template_id
            """;
    private static final String WATCHERS =
            "SELECT character_id FROM market_watch WHERE item_template_id = ? AND max_unit_price >= ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresMarketWatchAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Upsert upsert(WatchRequest request, int maxEntries) {
        long characterId = request.characterId();
        long itemTemplateId = request.itemTemplateId();
        return JdbcTransactionRunner.call(dataSource, connection -> {
            if (JdbcOperations.queryOne(connection, TEMPLATE_EXISTS, rs -> true, itemTemplateId)
                    .isEmpty()) {
                return Upsert.UNKNOWN_ITEM;
            }
            JdbcOperations.queryOne(connection, LOCK_CHARACTER, rs -> rs.getLong(1), characterId);
            boolean exists = JdbcOperations.queryOne(connection, EXISTS, rs -> true, characterId, itemTemplateId)
                    .isPresent();
            int count = JdbcOperations.queryOne(connection, COUNT, rs -> rs.getInt(1), characterId)
                    .orElse(0);
            if (!exists && count >= maxEntries) {
                return Upsert.TOO_MANY;
            }
            JdbcOperations.update(
                    connection,
                    UPSERT,
                    characterId,
                    itemTemplateId,
                    request.maxUnitPrice(),
                    Timestamp.from(request.at()));
            return Upsert.OK;
        });
    }

    @Override
    public boolean remove(long characterId, long itemTemplateId) {
        return JdbcOperations.update(dataSource, REMOVE, characterId, itemTemplateId) > 0;
    }

    @Override
    public List<Watch> list(long characterId) {
        return JdbcOperations.query(dataSource, LIST, rs -> new Watch(rs.getLong(1), rs.getLong(2)), characterId);
    }

    @Override
    public List<Long> watchersFor(long itemTemplateId, long unitPrice) {
        return JdbcOperations.query(dataSource, WATCHERS, rs -> rs.getLong(1), itemTemplateId, unitPrice);
    }
}
