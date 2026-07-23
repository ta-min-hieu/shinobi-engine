package dev.kage.server.database.mail;

import dev.kage.mail.domain.InventoryGatewayPort;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

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
        var connection = ((JdbcTransaction) tx).connection();
        try {
            int existingCount = countItems(connection, characterId);
            if (existingCount >= DEFAULT_CAPACITY) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement(INSERT_ITEM)) {
                statement.setLong(1, characterId);
                statement.setLong(2, itemTemplateId);
                statement.setInt(3, quantity);
                statement.setInt(4, existingCount); // slot_index — nối vào cuối danh sách hiện có
                statement.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi cộng item từ thư: characterId=" + characterId, e);
        }
    }

    private static int countItems(Connection connection, long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COUNT_ITEMS)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
