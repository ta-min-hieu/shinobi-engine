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

/**
 * Kho chung tài khoản (ADR-197, V154) — cùng hợp đồng {@link StoragePort} với kho tông môn, nhưng mọi câu đi từ
 * {@code characterId} sang {@code account_id} của nhân vật đó. Khoá dòng {@code account} trước khi khoá dòng kho: hai
 * nhân vật cùng tài khoản (hai phiên) không cùng lọt trần sức chứa.
 */
public final class PostgresAccountStorageAdapter implements StoragePort {

    private static final String LIST = """
            SELECT s.id, s.item_template_id, s.quantity, s.enhance_level
              FROM account_storage_item s JOIN character c ON c.account_id = s.account_id
             WHERE c.id = ? ORDER BY s.id
            """;

    private static final String LOCK_ACCOUNT = """
            SELECT a.id FROM account a JOIN character c ON c.account_id = a.id WHERE c.id = ? FOR UPDATE OF a
            """;

    private static final String LIST_FOR_UPDATE = """
            SELECT s.id, s.item_template_id, s.quantity, s.enhance_level
              FROM account_storage_item s JOIN character c ON c.account_id = s.account_id
             WHERE c.id = ? ORDER BY s.id FOR UPDATE OF s
            """;

    private static final String INSERT = """
            INSERT INTO account_storage_item (account_id, item_template_id, quantity, enhance_level, stored_at)
            SELECT c.account_id, ?, ?, ?, ? FROM character c WHERE c.id = ?
            """;

    private static final String UPDATE_QUANTITY = "UPDATE account_storage_item SET quantity = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM account_storage_item WHERE id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresAccountStorageAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<StoredItem> list(long characterId) {
        return JdbcOperations.query(dataSource, LIST, PostgresAccountStorageAdapter::row, characterId);
    }

    @Override
    public List<StoredItem> listForUpdate(Transaction tx, long characterId) {
        JdbcOperations.queryOne(connection(tx), LOCK_ACCOUNT, rs -> rs.getLong(1), characterId);
        return JdbcOperations.query(connection(tx), LIST_FOR_UPDATE, PostgresAccountStorageAdapter::row, characterId);
    }

    @Override
    public void insert(Transaction tx, long characterId, StoredItem item) {
        JdbcOperations.update(
                connection(tx),
                INSERT,
                item.itemTemplateId(),
                item.quantity(),
                item.enhanceLevel(),
                OffsetDateTime.now(ZoneOffset.UTC),
                characterId);
    }

    @Override
    public void updateQuantity(Transaction tx, long storedItemId, int quantity) {
        JdbcOperations.update(connection(tx), UPDATE_QUANTITY, quantity, storedItemId);
    }

    @Override
    public void delete(Transaction tx, long storedItemId) {
        JdbcOperations.update(connection(tx), DELETE, storedItemId);
    }

    @Override
    public String auditScope() {
        return "ACCOUNT_STORAGE";
    }

    private static StoredItem row(ResultSet rs) throws SQLException {
        return new StoredItem(
                rs.getLong("id"), rs.getLong("item_template_id"), rs.getInt("quantity"), rs.getInt("enhance_level"));
    }

    private static Connection connection(Transaction tx) {
        return ((JdbcTransaction) tx).connection();
    }
}
