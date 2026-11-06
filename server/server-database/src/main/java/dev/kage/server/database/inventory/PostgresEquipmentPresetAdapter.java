package dev.kage.server.database.inventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import dev.kage.inventory.domain.EquipmentPresetPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link EquipmentPresetPort} (ADR-175, V140). */
public final class PostgresEquipmentPresetAdapter implements EquipmentPresetPort {

    private static final String SELECT =
            "SELECT preset_no, slot, item_id FROM equipment_preset WHERE character_id = ? ORDER BY preset_no";
    private static final String DELETE = "DELETE FROM equipment_preset WHERE character_id = ? AND preset_no = ?";
    private static final String INSERT =
            "INSERT INTO equipment_preset (character_id, preset_no, slot, item_id) VALUES (?, ?, ?, ?)";

    private final DataSource dataSource;

    public PostgresEquipmentPresetAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Một dòng đọc lên. */
    private record Row(int presetNo, String slot, long itemId) {}

    @Override
    public Map<Integer, Map<EquipmentSlotType, Long>> presetsOf(long characterId) {
        Map<Integer, Map<EquipmentSlotType, Long>> presets = new TreeMap<>();
        for (Row row : JdbcOperations.query(
                dataSource, SELECT, rs -> new Row(rs.getInt(1), rs.getString(2), rs.getLong(3)), characterId)) {
            presets.computeIfAbsent(row.presetNo(), no -> new EnumMap<>(EquipmentSlotType.class))
                    .put(EquipmentSlotType.valueOf(row.slot()), row.itemId());
        }
        return presets;
    }

    @Override
    public void replace(long characterId, int presetNo, Map<EquipmentSlotType, Long> slots) {
        List<Object[]> rows = new ArrayList<>();
        slots.forEach((slot, itemId) -> rows.add(new Object[] {characterId, (short) presetNo, slot.name(), itemId}));
        JdbcTransactionRunner.run(dataSource, connection -> {
            JdbcOperations.update(connection, DELETE, characterId, (short) presetNo);
            if (!rows.isEmpty()) {
                JdbcOperations.batchUpdate(connection, INSERT, rows);
            }
        });
    }
}
