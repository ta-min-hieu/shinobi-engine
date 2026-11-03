package dev.kage.server.database.inventory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import dev.kage.inventory.domain.ItemLockPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link ItemLockPort} (ADR-159, V128). Bật/tắt mở đầu bằng {@link CharacterInventoryLock}: mọi đường
 * bán/thư/giao dịch cũng lấy khoá đó đầu tiên, nên câu kiểm "đã khoá chưa" của chúng là một câu lệnh MỚI chạy sau
 * khi lệnh khoá commit — không có khe "vừa khoá xong vẫn bán được".
 */
public final class PostgresItemLockAdapter implements ItemLockPort {

    private static final String SET_LOCKED = "UPDATE inventory_item SET locked = ? WHERE id = ? AND character_id = ?";

    private static final String LOCKED_ITEMS = "SELECT id FROM inventory_item WHERE character_id = ? AND locked";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresItemLockAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Outcome setLocked(long characterId, long itemId, boolean locked) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            CharacterInventoryLock.acquire(connection, characterId);
            if (JdbcOperations.update(connection, SET_LOCKED, locked, itemId, characterId) == 0) {
                return Outcome.NOT_FOUND;
            }
            JdbcOperations.update(
                    connection,
                    AUDIT,
                    characterId,
                    locked ? "ITEM_LOCKED" : "ITEM_UNLOCKED",
                    "{\"itemId\":" + itemId + "}",
                    Timestamp.from(Instant.now()));
            return Outcome.OK;
        });
    }

    @Override
    public Set<Long> lockedItems(long characterId) {
        return new HashSet<>(JdbcOperations.query(dataSource, LOCKED_ITEMS, rs -> rs.getLong(1), characterId));
    }
}
