package dev.kage.server.database.inventory;

import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Hiện thực {@link EquipmentPort} trên PostgreSQL (Điều 24 + DATABASE.md §11). Tối đa 7 dòng/nhân
 * vật ({@code EquipmentSlotType}) — xoá hết ghi lại mỗi lần save AN TOÀN ở đây (khác
 * {@link PostgresInventoryAdapter}): không có bảng nào tham chiếu ngược {@code equipment_slot}
 * theo ID (bảng còn không có cột {@code id}, khoá chính là {@code (character_id, slot_type)}).
 */
public final class PostgresEquipmentAdapter implements EquipmentPort {

    private static final String FIND_BY_CHARACTER = """
            SELECT slot_type, inventory_item_id FROM equipment_slot
             WHERE character_id = ? AND inventory_item_id IS NOT NULL
            """;

    private static final String DELETE_BY_CHARACTER = """
            DELETE FROM equipment_slot WHERE character_id = ?
            """;

    private static final String INSERT_SLOT = """
            INSERT INTO equipment_slot (character_id, slot_type, inventory_item_id) VALUES (?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresEquipmentAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Equipment> findByCharacter(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BY_CHARACTER)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                Map<EquipmentSlotType, Long> slots = new EnumMap<>(EquipmentSlotType.class);
                while (rs.next()) {
                    slots.put(
                            EquipmentSlotType.valueOf(rs.getString("slot_type")), rs.getLong("inventory_item_id"));
                }
                return Optional.of(new Equipment(characterId, slots));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc trang bị: characterId=" + characterId, e);
        }
    }

    @Override
    public void save(Transaction tx, Equipment equipment) {
        var connection = ((JdbcTransaction) tx).connection();
        try {
            deleteExisting(connection, equipment.characterId());
            insertSlots(connection, equipment);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi lưu trang bị: characterId=" + equipment.characterId(), e);
        }
    }

    private static void deleteExisting(Connection connection, long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_BY_CHARACTER)) {
            statement.setLong(1, characterId);
            statement.executeUpdate();
        }
    }

    private static void insertSlots(Connection connection, Equipment equipment) throws SQLException {
        Map<EquipmentSlotType, Long> slots = equipment.slots();
        if (slots.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SLOT)) {
            for (var entry : slots.entrySet()) {
                statement.setLong(1, equipment.characterId());
                statement.setString(2, entry.getKey().name());
                statement.setLong(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
