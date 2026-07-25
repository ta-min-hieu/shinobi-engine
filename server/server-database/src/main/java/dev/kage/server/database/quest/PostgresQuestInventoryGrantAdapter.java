package dev.kage.server.database.quest;

import dev.kage.quest.domain.QuestInventoryGrantPort;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

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
        try {
            Long itemTemplateId = findItemTemplateId(connection, itemTemplateCode);
            if (itemTemplateId == null) {
                return false;
            }
            int existingCount = countItems(connection, characterId);
            if (existingCount >= DEFAULT_CAPACITY) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement(INSERT_ITEM)) {
                statement.setLong(1, characterId);
                statement.setLong(2, itemTemplateId);
                statement.setInt(3, quantity);
                statement.setInt(4, existingCount);
                statement.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi cấp item thưởng quest: characterId=" + characterId, e);
        }
    }

    private static Long findItemTemplateId(Connection connection, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_ITEM_TEMPLATE_ID_BY_CODE)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
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
