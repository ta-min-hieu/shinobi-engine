package dev.kage.server.database.mail;

import java.sql.Connection;

import javax.sql.DataSource;

import dev.kage.mail.domain.InventoryGatewayPort;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link InventoryGatewayPort} trên PostgreSQL — SQL riêng cho {@code inventory_item},
 * bản sao ý tưởng {@code dev.kage.server.database.shop.PostgresShopInventoryGatewayAdapter}, chỉ
 * giữ chiều CẤP (nhận thư không có nhánh trừ item).
 */
public final class PostgresMailInventoryGatewayAdapter implements InventoryGatewayPort {

    // PLACEHOLDER trùng PostgresInventoryAdapter.DEFAULT_CAPACITY (GDD chưa chốt sức chứa thật).
    private static final int DEFAULT_CAPACITY = 20;

    private static final String COUNT_ITEMS = """
            SELECT COUNT(*) FROM inventory_item WHERE character_id = ?
            """;

    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, 0, ?)
            """;

    private final DataSource dataSource;

    public PostgresMailInventoryGatewayAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
        int existingCount = JdbcOperations.queryOne(connection, COUNT_ITEMS, rs -> rs.getInt(1), characterId)
                .orElse(0);
        if (existingCount >= DEFAULT_CAPACITY) {
            return false;
        }
        // slot_index — nối vào cuối danh sách hiện có.
        JdbcOperations.update(connection, INSERT_ITEM, characterId, itemTemplateId, quantity, existingCount);
        return true;
    }
}
