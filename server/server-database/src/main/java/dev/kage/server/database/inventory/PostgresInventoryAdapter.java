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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * Hiện thực {@link InventoryPort} trên PostgreSQL. SQL chỉ tồn tại ở đây (Điều 24 + DATABASE.md
 * §11). Luôn {@link PreparedStatement}, không nối chuỗi (SECURITY.md §11).
 *
 * <p>Không có bảng "inventory" riêng — {@link #findByCharacter} LUÔN trả {@code Optional} có giá
 * trị, lắp từ các dòng {@code inventory_item} hiện có (feature-inventory/CLAUDE.md).
 *
 * <p>{@link #save} là UPSERT THẬT (insert/update/delete theo {@code ItemStack.id}), KHÔNG còn
 * "xoá hết ghi lại" như lát cắt trước — {@code inventory_item.id} giờ là định danh ổn định mà
 * {@code equipment_slot.inventory_item_id} (Phase 5 "mặc đồ") cần tham chiếu đúng 1 dòng, xoá-ghi-
 * lại sẽ cấp ID mới mỗi lần save và làm gãy tham chiếu đó.
 */
public final class PostgresInventoryAdapter implements InventoryPort {

    // Sức chứa túi đồ — PLACEHOLDER, GDD chưa chốt (cùng tinh thần STARTING_STAT ở
    // PostgresCharacterAdapter). Chưa đọc từ character (level/VIP...) — lát cắt sau nếu cần.
    private static final int DEFAULT_CAPACITY = 20;

    private static final String FIND_BY_CHARACTER = """
            SELECT id, item_template_id, quantity, enhance_level
              FROM inventory_item WHERE character_id = ? ORDER BY id
            """;

    private static final String SELECT_EXISTING_IDS = """
            SELECT id FROM inventory_item WHERE character_id = ?
            """;

    private static final String DELETE_ITEM = """
            DELETE FROM inventory_item WHERE id = ?
            """;

    private static final String UPDATE_ITEM = """
            UPDATE inventory_item SET quantity = ?, enhance_level = ?, slot_index = ? WHERE id = ?
            """;

    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id
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
                    items.add(new ItemStack(rs.getLong("id"), rs.getLong("item_template_id"),
                            rs.getInt("quantity"), rs.getInt("enhance_level")));
                }
                return Optional.of(new Inventory(characterId, DEFAULT_CAPACITY, items));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc túi đồ: characterId=" + characterId, e);
        }
    }

    @Override
    public Inventory save(Transaction tx, Inventory inventory) {
        var connection = ((JdbcTransaction) tx).connection();
        try {
            deleteRemovedItems(connection, inventory);
            List<ItemStack> savedItems = upsertItems(connection, inventory);
            return new Inventory(inventory.characterId(), inventory.capacity(), savedItems);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi lưu túi đồ: characterId=" + inventory.characterId(), e);
        }
    }

    /** Item đã tồn tại DB (có id) nhưng KHÔNG còn trong {@code inventory.items()} — đã bị gỡ, xoá thật. */
    private static void deleteRemovedItems(Connection connection, Inventory inventory) throws SQLException {
        Set<Long> existingIds = selectExistingIds(connection, inventory.characterId());
        Set<Long> keptIds = inventory.items().stream()
                .filter(ItemStack::isPersisted)
                .map(ItemStack::id)
                .collect(Collectors.toSet());
        existingIds.removeAll(keptIds);
        if (existingIds.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(DELETE_ITEM)) {
            for (long id : existingIds) {
                statement.setLong(1, id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Set<Long> selectExistingIds(Connection connection, long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_EXISTING_IDS)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                var ids = new HashSet<Long>();
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                }
                return ids;
            }
        }
    }

    /**
     * Item đã có id thật → UPDATE tại chỗ (giữ nguyên id); item mới ({@code id == NEW_ITEM_ID}) →
     * INSERT, gán id thật từ {@code RETURNING id}. Trả về danh sách ĐÃ LƯU, CÙNG THỨ TỰ với
     * {@code inventory.items()} — caller (VD {@code GrantLootUseCase}) dựa vào thứ tự này để tra
     * lại ID thật của item mình vừa thêm (xem {@link InventoryPort#save}).
     */
    private static List<ItemStack> upsertItems(Connection connection, Inventory inventory) throws SQLException {
        List<ItemStack> items = inventory.items();
        var saved = new ArrayList<ItemStack>(items.size());
        for (int slotIndex = 0; slotIndex < items.size(); slotIndex++) {
            var stack = items.get(slotIndex);
            if (stack.isPersisted()) {
                updateItem(connection, stack, slotIndex);
                saved.add(stack);
            } else {
                long realId = insertItem(connection, inventory.characterId(), stack, slotIndex);
                saved.add(new ItemStack(realId, stack.itemTemplateId(), stack.quantity(), stack.enhanceLevel()));
            }
        }
        return saved;
    }

    private static void updateItem(Connection connection, ItemStack stack, int slotIndex) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_ITEM)) {
            statement.setInt(1, stack.quantity());
            statement.setInt(2, stack.enhanceLevel());
            statement.setInt(3, slotIndex);
            statement.setLong(4, stack.id());
            statement.executeUpdate();
        }
    }

    private static long insertItem(Connection connection, long characterId, ItemStack stack, int slotIndex)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ITEM)) {
            statement.setLong(1, characterId);
            statement.setLong(2, stack.itemTemplateId());
            statement.setInt(3, stack.quantity());
            statement.setInt(4, stack.enhanceLevel());
            statement.setInt(5, slotIndex);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1); // RETURNING id
            }
        }
    }
}
