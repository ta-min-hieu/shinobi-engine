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
 * Kho bang (ADR-198, V155) — cùng hợp đồng {@link StoragePort} với kho tông môn, mọi câu đi từ {@code characterId} sang
 * bang người đó đang ở. Khoá dòng {@code guild} trước các dòng kho (hai thành viên gửi cùng lúc không cùng lọt trần).
 * Mỗi lần gửi / rút ghi một dòng nhật ký bang trong CÙNG transaction ({@link #recorded}).
 */
public final class PostgresGuildStorageAdapter implements StoragePort {

    private static final String LIST = """
            SELECT s.id, s.item_template_id, s.quantity, s.enhance_level
              FROM guild_storage_item s JOIN guild_member m ON m.guild_id = s.guild_id
             WHERE m.character_id = ? ORDER BY s.id
            """;

    private static final String LOCK_GUILD = """
            SELECT g.id FROM guild g JOIN guild_member m ON m.guild_id = g.id WHERE m.character_id = ? FOR UPDATE OF g
            """;

    private static final String LIST_FOR_UPDATE = """
            SELECT s.id, s.item_template_id, s.quantity, s.enhance_level
              FROM guild_storage_item s JOIN guild_member m ON m.guild_id = s.guild_id
             WHERE m.character_id = ? ORDER BY s.id FOR UPDATE OF s
            """;

    private static final String INSERT = """
            INSERT INTO guild_storage_item (guild_id, item_template_id, quantity, enhance_level, stored_at)
            SELECT m.guild_id, ?, ?, ?, ? FROM guild_member m WHERE m.character_id = ?
            """;

    private static final String UPDATE_QUANTITY = "UPDATE guild_storage_item SET quantity = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM guild_storage_item WHERE id = ?";

    private static final String LOG = """
            INSERT INTO guild_log (guild_id, kind, actor_id, subject_id, amount, detail, created_at)
            SELECT m.guild_id, ?, m.character_id, NULL, ?, (SELECT code FROM item_template WHERE id = ?), ?
              FROM guild_member m WHERE m.character_id = ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildStorageAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<StoredItem> list(long characterId) {
        return JdbcOperations.query(dataSource, LIST, PostgresGuildStorageAdapter::row, characterId);
    }

    @Override
    public List<StoredItem> listForUpdate(Transaction tx, long characterId) {
        JdbcOperations.queryOne(connection(tx), LOCK_GUILD, rs -> rs.getLong(1), characterId);
        return JdbcOperations.query(connection(tx), LIST_FOR_UPDATE, PostgresGuildStorageAdapter::row, characterId);
    }

    /**
     * @throws IllegalStateException nếu người gửi không còn ở bang nào (vừa rời / bị đuổi sau lần gateway kiểm) — cả
     *     transaction lùi lại, món không mất khỏi túi
     */
    @Override
    public void insert(Transaction tx, long characterId, StoredItem item) {
        int inserted = JdbcOperations.update(
                connection(tx),
                INSERT,
                item.itemTemplateId(),
                item.quantity(),
                item.enhanceLevel(),
                OffsetDateTime.now(ZoneOffset.UTC),
                characterId);
        if (inserted == 0) {
            throw new IllegalStateException("Không còn ở bang nào — huỷ gửi kho bang: characterId=" + characterId);
        }
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
        return "GUILD_BANK";
    }

    @Override
    public void recorded(Transaction tx, Movement movement) {
        JdbcOperations.update(
                connection(tx),
                LOG,
                movement.deposit() ? "BANK_DEPOSIT" : "BANK_WITHDRAW",
                movement.quantity(),
                movement.itemTemplateId(),
                OffsetDateTime.now(ZoneOffset.UTC),
                movement.characterId());
    }

    private static StoredItem row(ResultSet rs) throws SQLException {
        return new StoredItem(
                rs.getLong("id"), rs.getLong("item_template_id"), rs.getInt("quantity"), rs.getInt("enhance_level"));
    }

    private static Connection connection(Transaction tx) {
        return ((JdbcTransaction) tx).connection();
    }
}
