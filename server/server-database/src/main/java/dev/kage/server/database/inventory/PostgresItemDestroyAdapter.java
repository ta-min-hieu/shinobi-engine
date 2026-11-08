package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.inventory.domain.ItemDestroyPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link ItemDestroyPort} (ADR-231). Khoá túi → khoá dòng → kiểm khoá / đang mặc / đang chào giao dịch → xoá
 * (kèm ngọc khảm, tẩy luyện, trạng thái linh thú của món theo CASCADE / dọn tay) → audit {@code ITEM_DESTROYED}.
 */
public final class PostgresItemDestroyAdapter implements ItemDestroyPort {

    private static final String LOCK_ITEM = "SELECT ii.item_template_id, ii.quantity, ii.enhance_level, ii.locked,"
            + " EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id) AS equipped, "
            + TradeOfferedQuantity.OF_II + " AS offered"
            + " FROM inventory_item ii WHERE ii.id = ? AND ii.character_id = ? FOR UPDATE OF ii";

    private static final String[] CLEANUP = {
        "DELETE FROM item_socket WHERE inventory_item_id = ?",
        "DELETE FROM item_refinement WHERE inventory_item_id = ?",
        "DELETE FROM pet_state WHERE inventory_item_id = ?",
        "DELETE FROM inventory_item WHERE id = ?"
    };

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Dòng bị chọn. */
    private record Row(
            long templateId, int quantity, int enhanceLevel, boolean locked, boolean equipped, int offered) {}

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresItemDestroyAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Outcome destroy(long characterId, long itemId) {
        return JdbcTransactionRunner.call(dataSource, connection -> destroyIn(connection, characterId, itemId));
    }

    private static Outcome destroyIn(Connection connection, long characterId, long itemId) {
        CharacterInventoryLock.acquire(connection, characterId);
        Optional<Row> row = JdbcOperations.queryOne(
                connection,
                LOCK_ITEM,
                rs -> new Row(
                        rs.getLong("item_template_id"),
                        rs.getInt("quantity"),
                        rs.getInt("enhance_level"),
                        rs.getBoolean("locked"),
                        rs.getBoolean("equipped"),
                        rs.getInt("offered")),
                itemId,
                characterId);
        if (row.isEmpty()) {
            return Outcome.NOT_FOUND;
        }
        Row r = row.get();
        if (r.locked()) {
            return Outcome.LOCKED;
        }
        if (r.equipped()) {
            return Outcome.EQUIPPED;
        }
        if (r.offered() > 0) {
            return Outcome.IN_TRADE;
        }
        for (String sql : CLEANUP) {
            JdbcOperations.update(connection, sql, itemId);
        }
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "ITEM_DESTROYED",
                "{\"itemId\":" + itemId + ",\"templateId\":" + r.templateId() + ",\"quantity\":" + r.quantity()
                        + ",\"enhanceLevel\":" + r.enhanceLevel() + "}",
                Timestamp.from(Instant.now()));
        return Outcome.OK;
    }
}
