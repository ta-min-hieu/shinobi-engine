package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

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
        List<SlotRow> rows = JdbcOperations.query(
                dataSource,
                FIND_BY_CHARACTER,
                rs -> new SlotRow(
                        EquipmentSlotType.valueOf(rs.getString("slot_type")), rs.getLong("inventory_item_id")),
                characterId);
        Map<EquipmentSlotType, Long> slots = new EnumMap<EquipmentSlotType, Long>(EquipmentSlotType.class);
        for (SlotRow row : rows) {
            slots.put(row.slotType(), row.inventoryItemId());
        }
        return Optional.of(new Equipment(characterId, slots));
    }

    @Override
    public void save(Transaction tx, Equipment equipment) {
        Connection connection = ((JdbcTransaction) tx).connection();
        JdbcOperations.update(connection, DELETE_BY_CHARACTER, equipment.characterId());
        insertSlots(connection, equipment);
    }

    private static void insertSlots(Connection connection, Equipment equipment) {
        Map<EquipmentSlotType, Long> slots = equipment.slots();
        if (slots.isEmpty()) {
            return;
        }
        List<Object[]> batchParams = new ArrayList<Object[]>();
        for (Map.Entry<EquipmentSlotType, Long> entry : slots.entrySet()) {
            batchParams.add(
                    new Object[] {equipment.characterId(), entry.getKey().name(), entry.getValue()});
        }
        JdbcOperations.batchUpdate(connection, INSERT_SLOT, batchParams);
    }

    private record SlotRow(EquipmentSlotType slotType, long inventoryItemId) {}
}
