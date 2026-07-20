package dev.kage.server.database.inventory;

import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Hiện thực {@link InventoryPort} trên PostgreSQL. SQL chỉ tồn tại ở đây (Điều 24 + DATABASE.md
 * §11). Luôn {@link PreparedStatement}, không nối chuỗi (SECURITY.md §11).
 *
 * <p>Không có bảng "inventory" riêng — {@link #findByCharacter} LUÔN trả {@code Optional} có giá
 * trị, lắp từ các dòng {@code inventory_item} hiện có (feature-inventory/CLAUDE.md).
 */
public final class PostgresInventoryAdapter implements InventoryPort {

    // Sức chứa túi đồ — PLACEHOLDER, GDD chưa chốt (cùng tinh thần STARTING_STAT ở
    // PostgresCharacterAdapter). Chưa đọc từ character (level/VIP...) — lát cắt sau nếu cần.
    private static final int DEFAULT_CAPACITY = 20;

    private static final String FIND_BY_CHARACTER = """
            SELECT item_template_id, quantity, enhance_level
              FROM inventory_item WHERE character_id = ? ORDER BY slot_index
            """;

    private static final String DELETE_BY_CHARACTER = """
            DELETE FROM inventory_item WHERE character_id = ?
            """;

    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresInventoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Inventory> findByCharacter(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BY_CHARACTER)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                var items = new ArrayList<ItemStack>();
                while (rs.next()) {
                    items.add(new ItemStack(rs.getLong("item_template_id"), rs.getInt("quantity"),
                            rs.getInt("enhance_level")));
                }
                return Optional.of(new Inventory(characterId, DEFAULT_CAPACITY, items));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc túi đồ: characterId=" + characterId, e);
        }
    }

    @Override
    public void save(Transaction tx, Inventory inventory) {
        var connection = ((JdbcTransaction) tx).connection();
        try {
            deleteExisting(connection, inventory.characterId());
            insertAll(connection, inventory);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi lưu túi đồ: characterId=" + inventory.characterId(), e);
        }
    }

    private static void deleteExisting(Connection connection, long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_BY_CHARACTER)) {
            statement.setLong(1, characterId);
            statement.executeUpdate();
        }
    }

    private static void insertAll(Connection connection, Inventory inventory) throws SQLException {
        List<ItemStack> items = inventory.items();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ITEM)) {
            for (int slotIndex = 0; slotIndex < items.size(); slotIndex++) {
                var stack = items.get(slotIndex);
                statement.setLong(1, inventory.characterId());
                statement.setLong(2, stack.itemTemplateId());
                statement.setInt(3, stack.quantity());
                statement.setInt(4, stack.enhanceLevel());
                statement.setInt(5, slotIndex);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
