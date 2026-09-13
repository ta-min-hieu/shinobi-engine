package dev.kage.server.database.quest;

import java.sql.Connection;
import java.util.Optional;

import dev.kage.quest.domain.QuestInventoryGrantPort;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.inventory.InventoryStackMerge;

/**
 * Hiện thực {@link QuestInventoryGrantPort} trên PostgreSQL — SQL riêng cho {@code inventory_item}
 * (cùng lý do KHÔNG tái dùng {@code Inventory} aggregate của feature-inventory như
 * {@code PostgresShopInventoryGatewayAdapter}: bắc cầu 2 loại {@code Transaction} khác nhau tốn
 * hơn trùng lặp vài câu SQL nhỏ). Nhận {@code itemTemplateCode} — tự tra {@code item_template.id}
 * nội bộ, feature-quest domain không cần biết ID số.
 *
 * <p>⚠️ Hạn chế ĐÃ BIẾT của {@link #hasRoomFor}: lô có NHIỀU món CÙNG loại xếp chồng được đếm
 * hơi thận trọng, vì mỗi lần gộp làm đổi chỗ trống còn lại mà phép đếm này soi trạng thái TRƯỚC
 * khi trao. Thận trọng là phía an toàn — từ chối oan thì người chơi dọn túi rồi lĩnh lại được,
 * còn nói nhầm "đủ chỗ" thì mất món vĩnh viễn, đúng bug 2026-09-13.
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

    // Soi ĐÚNG điều kiện của InventoryStackMerge.MERGE_INTO_EXISTING_STACK — nếu lệch thì guard sẽ
    // nói "đủ chỗ" trong khi lúc trao lại phải mở ô mới, tức đúng cái bug này sinh ra để chặn.
    private static final String COUNT_MERGEABLE_STACKS = """
            SELECT COUNT(*)
              FROM inventory_item ii
              JOIN item_template it ON it.id = ii.item_template_id
             WHERE ii.character_id = ?
               AND ii.item_template_id = ?
               AND ii.enhance_level = 0
               AND it.max_stack > 1
               AND ii.quantity + ? <= it.max_stack
            """;

    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, 0, ?)
            """;

    @Override
    public boolean hasRoomFor(Transaction tx, long characterId, java.util.List<ItemGrantRequest> grants) {
        Connection connection = ((JdbcTransaction) tx).connection();
        int used = JdbcOperations.queryOne(connection, COUNT_ITEMS, rs -> rs.getInt(1), characterId)
                .orElse(0);
        int newSlotsNeeded = 0;
        for (ItemGrantRequest grant : grants) {
            Optional<Long> itemTemplateId = JdbcOperations.queryOne(
                    connection, FIND_ITEM_TEMPLATE_ID_BY_CODE, rs -> rs.getLong("id"), grant.itemTemplateCode());
            if (itemTemplateId.isEmpty()) {
                continue; // mã sai thì grantItemByCode cũng bỏ qua — không tính là tốn ô.
            }
            int mergeable = JdbcOperations.queryOne(
                            connection,
                            COUNT_MERGEABLE_STACKS,
                            rs -> rs.getInt(1),
                            characterId,
                            itemTemplateId.get(),
                            grant.quantity())
                    .orElse(0);
            if (mergeable == 0) {
                newSlotsNeeded++;
            }
        }
        return newSlotsNeeded <= DEFAULT_CAPACITY - used;
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
        int existingCount = JdbcOperations.queryOne(connection, COUNT_ITEMS, rs -> rs.getInt(1), characterId)
                .orElse(0);
        if (existingCount >= DEFAULT_CAPACITY) {
            return false;
        }
        JdbcOperations.update(connection, INSERT_ITEM, characterId, itemTemplateId.get(), quantity, existingCount);
        return true;
    }
}
