package dev.kage.server.database.inventory;

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

import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

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

    /** ADR-064 D5 — món đang khảm Linh Ngọc. */
    // ADR-072 D3 — linh thú đã có cấp (dòng pet_state) chịu cùng luật với món đã khảm: đường dời bằng xoá/tạo dòng
    // sẽ làm mất nó. ADR-111 — món đã tẩy luyện cũng vậy (dòng item_refinement CASCADE theo dòng túi).
    private static final String IS_SOCKETED = """
            SELECT (SELECT COUNT(*) FROM item_socket WHERE inventory_item_id = ?)
                 + (SELECT COUNT(*) FROM pet_state WHERE inventory_item_id = ?)
                 + (SELECT COUNT(*) FROM item_refinement WHERE inventory_item_id = ?)
            """;

    // Đồ đang mặc không chiếm ô túi (quyết định chủ dự án 2026-10-11). `Inventory` chứa MỌI dòng
    // `inventory_item` của nhân vật, kể cả món đang mặc, và không biết món nào đang mặc — nên sức chứa
    // nó nhận là "20 ô túi + số món đang mặc": số món nằm trong túi vì thế vẫn bị chặn ở đúng 20.
    private static final String IS_LOCKED = "SELECT locked FROM inventory_item WHERE id = ?";

    private static final String COUNT_EQUIPPED = """
            SELECT COUNT(*) FROM equipment_slot WHERE character_id = ? AND inventory_item_id IS NOT NULL
            """;

    private static final String FIND_BY_CHARACTER = """
            SELECT id, item_template_id, quantity, enhance_level
              FROM inventory_item WHERE character_id = ? ORDER BY id
            """;

    // FOR UPDATE: xem InventoryPort#findOwnedItemForUpdate. Postgres đánh giá lại WHERE sau khi chờ
    // khoá, nên dòng vừa bị giao dịch đổi chủ sẽ KHÔNG khớp character_id nữa (rỗng, không phải dữ
    // liệu cũ).
    private static final String FIND_OWNED_ITEM_FOR_UPDATE = """
            SELECT id, item_template_id, quantity, enhance_level
              FROM inventory_item WHERE id = ? AND character_id = ?
               FOR UPDATE
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
        int equipped = JdbcOperations.queryOne(dataSource, COUNT_EQUIPPED, rs -> rs.getInt(1), characterId)
                .orElse(0);
        return Optional.of(new Inventory(characterId, BagCapacity.of(dataSource, characterId) + equipped, items));
    }

    /** ADR-088 — Túi Càn Khôn: một câu UPDATE có điều kiện, sau khoá túi đồ của giao dịch dùng vật phẩm. */
    @Override
    public boolean expandCapacity(Transaction tx, long characterId, int amount) {
        return BagCapacity.expand(((JdbcTransaction) tx).connection(), characterId, amount);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Khoá {@link CharacterInventoryLock} TRƯỚC rồi mới đọc: ở READ COMMITTED mỗi câu lệnh thấy
     * dữ liệu đã commit tại lúc nó chạy, nên câu đọc sau khoá thấy trọn kết quả của người giữ khoá
     * trước. Không cần {@code FOR UPDATE} từng dòng — mọi đường ghi túi đồ đều xếp hàng ở khoá này.
     */
    @Override
    public Optional<Inventory> findByCharacterForUpdate(Transaction tx, long characterId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        CharacterInventoryLock.acquire(connection, characterId);
        List<ItemStack> items = JdbcOperations.query(
                connection, FIND_BY_CHARACTER, PostgresInventoryAdapter::mapItemStack, characterId);
        int equipped = JdbcOperations.queryOne(connection, COUNT_EQUIPPED, rs -> rs.getInt(1), characterId)
                .orElse(0);
        return Optional.of(new Inventory(characterId, BagCapacity.of(connection, characterId) + equipped, items));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Khoá {@link CharacterInventoryLock} TRƯỚC khoá dòng</b> (sửa 2026-09-25). Chỉ khoá dòng
     * thì mặc đồ chặn được chiều "đồ rời túi trước, mặc sau", nhưng KHÔNG chặn chiều ngược: mặc tới
     * trước (đã ghi ô, chưa commit), bán/gửi thư tới sau chờ ở {@code FOR UPDATE} rồi đọc câu
     * {@code EXISTS (equipment_slot)} theo ảnh chụp CŨ của chính câu lệnh đó — Postgres chỉ đánh giá
     * lại dòng bị khoá, không đánh giá lại bảng khác — nên thấy "chưa mặc" và bán luôn món vừa mặc.
     * Xếp hàng ở khoá túi đồ (đường bán/thư/giao dịch đều lấy nó đầu tiên) thì câu kiểm của bên tới
     * sau là một câu lệnh MỚI, chạy sau khi lệnh mặc commit. Tái hiện ở
     * {@code EquipWhileItemLeavesIntegrationTest}.
     */
    @Override
    public Optional<ItemStack> findOwnedItemForUpdate(Transaction tx, long characterId, long itemId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        CharacterInventoryLock.acquire(connection, characterId);
        return JdbcOperations.queryOne(
                connection, FIND_OWNED_ITEM_FOR_UPDATE, PostgresInventoryAdapter::mapItemStack, itemId, characterId);
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
                connection,
                INSERT_ITEM,
                characterId,
                stack.itemTemplateId(),
                stack.quantity(),
                stack.enhanceLevel(),
                slotIndex);
    }

    @Override
    public boolean isSocketed(Transaction tx, long itemId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        return JdbcOperations.queryOne(connection, IS_SOCKETED, rs -> rs.getInt(1), itemId, itemId, itemId)
                        .orElse(0)
                > 0;
    }

    @Override
    public boolean isLocked(Transaction tx, long itemId) {
        return JdbcOperations.queryOne(((JdbcTransaction) tx).connection(), IS_LOCKED, rs -> rs.getBoolean(1), itemId)
                .orElse(false);
    }
}
