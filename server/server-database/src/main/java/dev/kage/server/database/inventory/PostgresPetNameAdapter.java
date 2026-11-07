package dev.kage.server.database.inventory;

import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.inventory.domain.PetNamePort;
import dev.kage.server.database.JdbcOperations;

/** ADR-193 — {@code pet_state.custom_name} của linh thú đang mặc; chưa có dòng {@code pet_state} thì tạo cấp 1. */
public final class PostgresPetNameAdapter implements PetNamePort {

    private static final String RENAME = """
            INSERT INTO pet_state (inventory_item_id, level, exp, custom_name)
            SELECT es.inventory_item_id, 1, 0, NULLIF(?, '')
              FROM equipment_slot es
             WHERE es.character_id = ? AND es.slot_type = 'PET'
            ON CONFLICT (inventory_item_id) DO UPDATE SET custom_name = EXCLUDED.custom_name
            """;

    private static final String NAME = """
            SELECT p.custom_name
              FROM equipment_slot es JOIN pet_state p ON p.inventory_item_id = es.inventory_item_id
             WHERE es.character_id = ? AND es.slot_type = 'PET' AND p.custom_name IS NOT NULL
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresPetNameAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean renameEquipped(long characterId, String name) {
        return JdbcOperations.update(dataSource, RENAME, name, characterId) > 0;
    }

    @Override
    public Optional<String> equippedName(long characterId) {
        return JdbcOperations.queryOne(dataSource, NAME, rs -> rs.getString(1), characterId);
    }
}
