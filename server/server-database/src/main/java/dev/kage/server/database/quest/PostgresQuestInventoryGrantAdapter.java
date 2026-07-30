package dev.kage.server.database.quest;

import java.sql.Connection;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.quest.domain.QuestInventoryGrantPort;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link QuestInventoryGrantPort} trên PostgreSQL — SQL riêng cho {@code inventory_item}
 * (cùng lý do KHÔNG tái dùng {@code Inventory} aggregate của feature-inventory như
 * {@code PostgresShopInventoryGatewayAdapter}: bắc cầu 2 loại {@code Transaction} khác nhau tốn
 * hơn trùng lặp vài câu SQL nhỏ). Nhận {@code itemTemplateCode} — tự tra {@code item_template.id}
 * nội bộ, feature-quest domain không cần biết ID số.
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

    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, 0, ?)
            """;

    private final DataSource dataSource;

    public PostgresQuestInventoryGrantAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean grantItemByCode(Transaction tx, long characterId, String itemTemplateCode, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
        Optional<Long> itemTemplateId = JdbcOperations.queryOne(
                connection, FIND_ITEM_TEMPLATE_ID_BY_CODE, rs -> rs.getLong("id"), itemTemplateCode);
        if (itemTemplateId.isEmpty()) {
            return false;
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
