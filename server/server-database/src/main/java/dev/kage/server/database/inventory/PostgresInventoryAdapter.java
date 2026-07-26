package dev.kage.server.database.inventory;

import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import java.sql.Connection;
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
 * §11). Luôn {@link java.sql.PreparedStatement} qua {@link JdbcOperations}, không nối chuỗi
 * (SECURITY.md §11).
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
        List<ItemStack> items = JdbcOperations.query(
                dataSource, FIND_BY_CHARACTER, PostgresInventoryAdapter::mapItemStack, characterId);
        return Optional.of(new Inventory(characterId, DEFAULT_CAPACITY, items));
    }

    @Override
    public Inventory save(Transaction tx, Inventory inventory) {
        Connection connection = ((JdbcTransaction) tx).connection();
        deleteRemovedItems(connection, inventory);
        List<ItemStack> savedItems = upsertItems(connection, inventory);
        return new Inventory(inventory.characterId(), inventory.capacity(), savedItems);
    }

    private static ItemStack mapItemStack(ResultSet rs) throws SQLException {
        return new ItemStack(
                rs.getLong("id"), rs.getLong("item_template_id"), rs.getInt("quantity"), rs.getInt("enhance_level"));
    }

    /** Item đã tồn tại DB (có id) nhưng KHÔNG còn trong {@code inventory.items()} — đã bị gỡ, xoá thật. */
    private static void deleteRemovedItems(Connection connection, Inventory inventory) {
        Set<Long> existingIds = selectExistingIds(connection, inventory.characterId());
        Set<Long> keptIds = inventory.items().stream()
                .filter(ItemStack::isPersisted)
                .map(ItemStack::id)
                .collect(Collectors.toSet());
        existingIds.removeAll(keptIds);
        if (existingIds.isEmpty()) {
            return;
        }
        List<Object[]> batchParams =
                existingIds.stream().map(id -> new Object[] {id}).collect(Collectors.toList());
        JdbcOperations.batchUpdate(connection, DELETE_ITEM, batchParams);
    }

    private static Set<Long> selectExistingIds(Connection connection, long characterId) {
        List<Long> ids = JdbcOperations.query(connection, SELECT_EXISTING_IDS, rs -> rs.getLong("id"), characterId);
        return new HashSet<Long>(ids);
    }

    /**
     * Item đã có id thật → UPDATE tại chỗ (giữ nguyên id); item mới ({@code id == NEW_ITEM_ID}) →
     * INSERT, gán id thật từ {@code RETURNING id}. Trả về danh sách ĐÃ LƯU, CÙNG THỨ TỰ với
     * {@code inventory.items()} — caller (VD {@code GrantLootUseCase}) dựa vào thứ tự này để tra
     * lại ID thật của item mình vừa thêm (xem {@link InventoryPort#save}).
     */
    private static List<ItemStack> upsertItems(Connection connection, Inventory inventory) {
        List<ItemStack> items = inventory.items();
        ArrayList<ItemStack> saved = new ArrayList<ItemStack>(items.size());
        for (int slotIndex = 0; slotIndex < items.size(); slotIndex++) {
            ItemStack stack = items.get(slotIndex);
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

    private static void updateItem(Connection connection, ItemStack stack, int slotIndex) {
        JdbcOperations.update(connection, UPDATE_ITEM, stack.quantity(), stack.enhanceLevel(), slotIndex, stack.id());
    }

    private static long insertItem(Connection connection, long characterId, ItemStack stack, int slotIndex) {
        return JdbcOperations.insertReturningId(
                connection, INSERT_ITEM, characterId, stack.itemTemplateId(), stack.quantity(), stack.enhanceLevel(),
                slotIndex);
    }
}
