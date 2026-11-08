package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.inventory.domain.StackSplitPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link StackSplitPort} (ADR-215). Khoá túi ({@link CharacterInventoryLock}) → khoá chồng → kiểm còn ô →
 * trừ chồng cũ, chèn chồng mới cùng mẫu / cấp cường hoá / cờ khoá ở cuối túi. Chồng đang mặc hoặc đang chào giao dịch
 * không tách được (phần đang chào không được đổi hình dạng giữa chừng).
 */
public final class PostgresStackSplitAdapter implements StackSplitPort {

    private static final String LOCK_STACK = "SELECT ii.item_template_id, ii.quantity, ii.enhance_level, ii.locked"
            + " FROM inventory_item ii"
            + " WHERE ii.id = ? AND ii.character_id = ?"
            + " AND NOT EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id)"
            + " AND " + TradeOfferedQuantity.OF_II + " = 0"
            + " FOR UPDATE OF ii";

    private static final String BAG_ROWS = """
            SELECT COUNT(*) FROM inventory_item ii
             WHERE ii.character_id = ?
               AND NOT EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id)
            """;

    private static final String DECREMENT = "UPDATE inventory_item SET quantity = quantity - ? WHERE id = ?";

    private static final String INSERT = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, locked, slot_index)
            VALUES (?, ?, ?, ?, ?, (SELECT COALESCE(MAX(slot_index) + 1, 0) FROM inventory_item WHERE character_id = ?))
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Chồng được chọn. */
    private record Stack(long templateId, int quantity, int enhanceLevel, boolean locked) {}

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresStackSplitAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Outcome split(long characterId, long inventoryItemId, int amount) {
        return JdbcTransactionRunner.call(
                dataSource, connection -> splitIn(connection, characterId, inventoryItemId, amount));
    }

    private static Outcome splitIn(Connection connection, long characterId, long inventoryItemId, int amount) {
        CharacterInventoryLock.acquire(connection, characterId);
        Optional<Stack> stack = JdbcOperations.queryOne(
                connection,
                LOCK_STACK,
                rs -> new Stack(
                        rs.getLong("item_template_id"),
                        rs.getInt("quantity"),
                        rs.getInt("enhance_level"),
                        rs.getBoolean("locked")),
                inventoryItemId,
                characterId);
        if (stack.isEmpty()) {
            return Outcome.NOT_FOUND;
        }
        if (amount >= stack.get().quantity()) {
            return Outcome.INVALID_AMOUNT;
        }
        int used = JdbcOperations.queryOne(connection, BAG_ROWS, rs -> rs.getInt(1), characterId)
                .orElse(0);
        if (used >= BagCapacity.of(connection, characterId)) {
            return Outcome.BAG_FULL;
        }
        Stack s = stack.get();
        JdbcOperations.update(connection, DECREMENT, amount, inventoryItemId);
        JdbcOperations.update(
                connection, INSERT, characterId, s.templateId(), amount, s.enhanceLevel(), s.locked(), characterId);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "STACK_SPLIT",
                "{\"itemId\":" + inventoryItemId + ",\"amount\":" + amount + "}",
                Timestamp.from(Instant.now()));
        return Outcome.OK;
    }
}
