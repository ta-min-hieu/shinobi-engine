package dev.kage.server.database.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.ItemUseEffect;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link ItemTemplatePort} trên PostgreSQL — đọc thuần, {@code item_template} là config tĩnh. */
public final class PostgresItemTemplateAdapter implements ItemTemplatePort {

    private static final String FIND_BY_ID = """
            SELECT id, equip_slot, max_stack, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level,
                   use_effect, use_value
              FROM item_template WHERE id = ?
            """;

    private static final String FIND_ID_BY_CODE = """
            SELECT id FROM item_template WHERE code = ?
            """;

    // ORDER BY id để thứ tự trả về ổn định giữa các lần chạy — ItemCatalog vẫn tự sắp lại trước khi
    // băm (không phụ thuộc DB), nhưng dữ liệu ổn định sẵn thì dễ soi khi debug.
    private static final String LIST_CATALOG = """
            SELECT id, code, name_key, max_stack, equip_slot,
                   bonus_atk, bonus_def, bonus_res, bonus_hp, required_level,
                   use_effect, use_value
              FROM item_template ORDER BY id
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

    @Override
    public List<ItemCatalogEntry> listCatalog() {
        return JdbcOperations.query(dataSource, LIST_CATALOG, PostgresItemTemplateAdapter::mapCatalogRow);
    }

    private static ItemCatalogEntry mapCatalogRow(ResultSet rs) throws SQLException {
        String equipSlot = rs.getString("equip_slot");
        return new ItemCatalogEntry(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name_key"),
                rs.getInt("max_stack"),
                equipSlot == null ? null : EquipmentSlotType.valueOf(equipSlot),
                rs.getInt("bonus_atk"),
                rs.getInt("bonus_def"),
                rs.getInt("bonus_res"),
                rs.getInt("bonus_hp"),
                rs.getInt("required_level"),
                parseUseEffect(rs.getString("use_effect")),
                rs.getInt("use_value"));
    }

    private static ItemTemplate mapRow(ResultSet rs) throws SQLException {
        String equipSlot = rs.getString("equip_slot");
        return new ItemTemplate(
                rs.getLong("id"),
                equipSlot == null ? null : EquipmentSlotType.valueOf(equipSlot),
                rs.getInt("max_stack"),
                rs.getInt("bonus_atk"),
                rs.getInt("bonus_def"),
                rs.getInt("bonus_res"),
                rs.getInt("bonus_hp"),
                rs.getInt("required_level"),
                parseUseEffect(rs.getString("use_effect")),
                rs.getInt("use_value"));
    }

    /**
     * ADR-046 D5 — {@code NULL} nghĩa là món không dùng được. Giá trị LẠ (dữ liệu tay viết sai
     * trong migration) cũng coi là không dùng được thay vì ném {@code IllegalArgumentException}:
     * một chuỗi gõ nhầm không được phép làm sập đường nạp túi đồ của mọi người chơi. Cùng luật mà
     * {@code WorldFacade#toStats} áp cho {@code element} lạ của quái (ADR-037).
     */
    private static ItemUseEffect parseUseEffect(String raw) {
        if (raw == null) {
            return null;
        }
        for (ItemUseEffect effect : ItemUseEffect.values()) {
            if (effect.name().equals(raw)) {
                return effect;
            }
        }
        return null;
    }
}
