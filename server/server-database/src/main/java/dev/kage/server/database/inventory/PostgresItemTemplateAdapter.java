package dev.kage.server.database.inventory;

import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.server.database.JdbcOperations;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

/** Hiện thực {@link ItemTemplatePort} trên PostgreSQL — đọc thuần, {@code item_template} là config tĩnh. */
public final class PostgresItemTemplateAdapter implements ItemTemplatePort {

    private static final String FIND_BY_ID = """
            SELECT id, equip_slot, bonus_atk FROM item_template WHERE id = ?
            """;

    private static final String FIND_ID_BY_CODE = """
            SELECT id FROM item_template WHERE code = ?
            """;

    private final DataSource dataSource;

    public PostgresItemTemplateAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ItemTemplate> findById(long itemTemplateId) {
        return JdbcOperations.queryOne(dataSource, FIND_BY_ID, PostgresItemTemplateAdapter::mapRow, itemTemplateId);
    }

    @Override
    public Optional<Long> findIdByCode(String code) {
        return JdbcOperations.queryOne(dataSource, FIND_ID_BY_CODE, rs -> rs.getLong("id"), code);
    }

    private static ItemTemplate mapRow(ResultSet rs) throws SQLException {
        String equipSlot = rs.getString("equip_slot");
        return new ItemTemplate(
                rs.getLong("id"),
                equipSlot == null ? null : EquipmentSlotType.valueOf(equipSlot),
                rs.getInt("bonus_atk"));
    }
}
