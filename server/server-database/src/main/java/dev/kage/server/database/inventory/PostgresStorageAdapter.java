package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.inventory.domain.StoragePort;
import dev.kage.inventory.domain.StoredItem;
import dev.kage.inventory.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link StoragePort} (ADR-061, V63) — ghi trong transaction của túi đồ. */
public final class PostgresStorageAdapter implements StoragePort {

    private static final String LIST = """
            SELECT id, item_template_id, quantity, enhance_level
            FROM storage_item WHERE character_id = ? ORDER BY id
            """;

    private static final String LIST_FOR_UPDATE = """
            SELECT id, item_template_id, quantity, enhance_level
            FROM storage_item WHERE character_id = ? ORDER BY id FOR UPDATE
            """;

    private static final String INSERT = """
            INSERT INTO storage_item (character_id, item_template_id, quantity, enhance_level, stored_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_QUANTITY = "UPDATE storage_item SET quantity = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM storage_item WHERE id = ?";

    private final DataSource dataSource;

    public PostgresStorageAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<StoredItem> list(long characterId) {
        return JdbcOperations.query(dataSource, LIST, PostgresStorageAdapter::row, characterId);
    }

    @Override
    public List<StoredItem> listForUpdate(Transaction tx, long characterId) {
        return JdbcOperations.query(connection(tx), LIST_FOR_UPDATE, PostgresStorageAdapter::row, characterId);
    }

    @Override
    public void insert(Transaction tx, long characterId, StoredItem item) {
        JdbcOperations.update(
                connection(tx),
                INSERT,
                characterId,
                item.itemTemplateId(),
                item.quantity(),
                item.enhanceLevel(),
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public void updateQuantity(Transaction tx, long storedItemId, int quantity) {
        JdbcOperations.update(connection(tx), UPDATE_QUANTITY, quantity, storedItemId);
    }

    @Override
    public void delete(Transaction tx, long storedItemId) {
        JdbcOperations.update(connection(tx), DELETE, storedItemId);
    }

    private static StoredItem row(ResultSet rs) throws SQLException {
        return new StoredItem(
                rs.getLong("id"), rs.getLong("item_template_id"), rs.getInt("quantity"), rs.getInt("enhance_level"));
    }

    private static Connection connection(Transaction tx) {
        return ((JdbcTransaction) tx).connection();
    }
}
