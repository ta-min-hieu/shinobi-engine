package dev.kage.server.database.quest;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.quest.domain.QuestInventoryGrantPort;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;

/**
 * Hiện thực {@link QuestInventoryGrantPort} trên PostgreSQL — SQL riêng cho {@code inventory_item}
 * (cùng lý do KHÔNG tái dùng {@code Inventory} aggregate của feature-inventory như
 * {@code PostgresShopInventoryGatewayAdapter}: bắc cầu 2 loại {@code Transaction} khác nhau tốn
 * hơn trùng lặp vài câu SQL nhỏ). Nhận {@code itemTemplateCode} — tự tra {@code item_template.id}
 * nội bộ, feature-quest domain không cần biết ID số.
 *
 * <p>{@link #hasRoomFor} DIỄN LẠI đúng thuật toán của {@link #grantItemByCode} trên một bản sao
 * các chồng trong bộ nhớ, theo đúng thứ tự script gọi (sửa 2026-10-02). Bản trước soi từng lời
 * {@code give_item} trên trạng thái túi TRƯỚC khi trao, và javadoc cũ gọi đó là "thận trọng" — sai:
 * hai lần 50 phi đao vào chồng 49/99 đều thấy "gộp được", nên đếm ra 0 ô mới, trong khi lúc trao
 * lần đầu lấp chồng tới 99 và lần sau phải mở ô mới. Tức là phép đếm LẠC QUAN, đúng chiều mất món.
 */
public final class PostgresQuestInventoryGrantAdapter implements QuestInventoryGrantPort {

    // PLACEHOLDER trùng PostgresInventoryAdapter.DEFAULT_CAPACITY/PostgresShopInventoryGatewayAdapter
    // (GDD chưa chốt sức chứa túi đồ thật) — đổi thì phải đổi ở cả 3 chỗ cho tới khi có nguồn chung.
    private static final int DEFAULT_CAPACITY = 20;

    private static final String FIND_ITEM_TEMPLATE_ID_BY_CODE = """
            SELECT id FROM item_template WHERE code = ?
            """;

    private static final String COUNT_ITEMS = """
            SELECT COUNT(*) FROM inventory_item WHERE character_id = ?
            """;

    private static final String FIND_MAX_STACK = """
            SELECT max_stack FROM item_template WHERE id = ?
            """;

    // Lọc ĐÚNG điều kiện của InventoryStackMerge.MERGE_INTO_EXISTING_STACK (trừ vế số lượng — vế đó
    // phép diễn lại tự tính) và cùng ORDER BY ii.id — nếu lệch thì guard sẽ nói "đủ chỗ" trong khi
    // lúc trao lại phải mở ô mới, tức đúng cái bug này sinh ra để chặn.
    private static final String LIST_MERGEABLE_STACK_QUANTITIES = """
            SELECT ii.quantity
              FROM inventory_item ii
              JOIN item_template it ON it.id = ii.item_template_id
             WHERE ii.character_id = ?
               AND ii.item_template_id = ?
               AND ii.enhance_level = 0
               AND it.max_stack > 1
             ORDER BY ii.id
            """;

    @Override
    public void lockInventory(Transaction tx, long characterId) {
        CharacterInventoryLock.acquire(((JdbcTransaction) tx).connection(), characterId);
    }

    /**
     * Diễn lại toàn bộ lô {@code give_item} trên bản sao các chồng hiện có, gom theo mã vật phẩm
     * (các mã khác nhau không đụng chồng của nhau), để đếm đúng số ô mới {@link #grantItemByCode}
     * sẽ mở. Mã không tồn tại không tốn ô — lúc trao nó trả {@code false} và use case rollback.
     */
    @Override
    public boolean hasRoomFor(Transaction tx, long characterId, List<ItemGrantRequest> grants) {
        Connection connection = ((JdbcTransaction) tx).connection();
        int used = JdbcOperations.queryOne(connection, COUNT_ITEMS, rs -> rs.getInt(1), characterId)
                .orElse(0);
        Map<String, List<Integer>> quantitiesByCode = new LinkedHashMap<>();
        for (ItemGrantRequest grant : grants) {
            quantitiesByCode
                    .computeIfAbsent(grant.itemTemplateCode(), code -> new ArrayList<>())
                    .add(grant.quantity());
        }
        int newSlotsNeeded = 0;
        for (Map.Entry<String, List<Integer>> entry : quantitiesByCode.entrySet()) {
            Optional<Long> itemTemplateId = JdbcOperations.queryOne(
                    connection, FIND_ITEM_TEMPLATE_ID_BY_CODE, rs -> rs.getLong("id"), entry.getKey());
            if (itemTemplateId.isPresent()) {
                newSlotsNeeded += newSlotsForSequence(connection, characterId, itemTemplateId.get(), entry.getValue());
            }
        }
        return newSlotsNeeded <= DEFAULT_CAPACITY - used;
    }

    /**
     * Số ô mới mà chuỗi lần trao {@code quantities} (cùng một loại, đúng thứ tự) sẽ mở — chép đúng
     * {@link InventoryStackMerge#mergeIntoExistingStack} (chồng id nhỏ nhất nhận TRỌN lần trao) rồi
     * {@link InventoryStackMerge#insertRespectingMaxStack} (tách theo {@code max_stack}; dòng mới
     * cũng thành chồng nhận được lần sau).
     */
    private static int newSlotsForSequence(
            Connection connection, long characterId, long itemTemplateId, List<Integer> quantities) {
        int maxStack = Math.max(
                1,
                JdbcOperations.queryOne(connection, FIND_MAX_STACK, rs -> rs.getInt(1), itemTemplateId)
                        .orElse(1));
        List<Integer> stacks = new ArrayList<>(JdbcOperations.query(
                connection, LIST_MERGEABLE_STACK_QUANTITIES, rs -> rs.getInt(1), characterId, itemTemplateId));
        int newSlots = 0;
        for (int quantity : quantities) {
            if (maxStack > 1 && mergeIntoFirstFittingStack(stacks, quantity, maxStack)) {
                continue;
            }
            int remaining = quantity;
            int rows = (quantity + maxStack - 1) / maxStack;
            for (int row = 0; row < rows; row++) {
                int rowQuantity = Math.min(maxStack, remaining);
                if (maxStack > 1) {
                    stacks.add(rowQuantity);
                }
                remaining -= rowQuantity;
            }
            newSlots += rows;
        }
        return newSlots;
    }

    private static boolean mergeIntoFirstFittingStack(List<Integer> stacks, int quantity, int maxStack) {
        for (int i = 0; i < stacks.size(); i++) {
            if (stacks.get(i) + quantity <= maxStack) {
                stacks.set(i, stacks.get(i) + quantity);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean grantItemByCode(Transaction tx, long characterId, String itemTemplateCode, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
        Optional<Long> itemTemplateId = JdbcOperations.queryOne(
                connection, FIND_ITEM_TEMPLATE_ID_BY_CODE, rs -> rs.getLong("id"), itemTemplateCode);
        if (itemTemplateId.isEmpty()) {
            return false;
        }
        // Gộp chồng TRƯỚC — cùng lý do ở PostgresShopInventoryGatewayAdapter, xem InventoryStackMerge.
        if (InventoryStackMerge.mergeIntoExistingStack(connection, characterId, itemTemplateId.get(), quantity)) {
            return true;
        }
        // Không gộp được ⇒ mở ô mới, tách theo max_stack (xem InventoryStackMerge#insertRespectingMaxStack).
        return InventoryStackMerge.insertRespectingMaxStack(connection, characterId, itemTemplateId.get(), quantity);
    }
}
