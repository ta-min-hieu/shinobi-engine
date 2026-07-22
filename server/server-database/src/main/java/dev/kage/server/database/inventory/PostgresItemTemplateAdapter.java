package dev.kage.server.database.inventory;

import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

/** Hiện thực {@link ItemTemplatePort} trên PostgreSQL — đọc thuần, {@code item_template} là config tĩnh. */
public final class PostgresItemTemplateAdapter implements ItemTemplatePort {

    private static final String FIND_BY_ID = """
            SELECT id, equip_slot FROM item_template WHERE id = ?
            """;

    private final DataSource dataSource;

    public PostgresItemTemplateAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ItemTemplate> findById(long itemTemplateId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setLong(1, itemTemplateId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String equipSlot = rs.getString("equip_slot");
                return Optional.of(new ItemTemplate(
                        rs.getLong("id"), equipSlot == null ? null : EquipmentSlotType.valueOf(equipSlot)));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc item_template: itemTemplateId=" + itemTemplateId, e);
        }
    }
}
