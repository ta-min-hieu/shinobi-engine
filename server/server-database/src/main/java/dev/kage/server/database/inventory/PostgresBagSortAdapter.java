package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.inventory.domain.BagSortPolicy;
import dev.kage.inventory.domain.BagSortPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link BagSortPort} (ADR-160). Mở đầu bằng {@link CharacterInventoryLock} (thứ tự khoá, DATABASE.md §5.1)
 * rồi khoá mọi dòng túi; luật gom/xếp nằm ở {@link BagSortPolicy}. Chỉ gom dòng KHÔNG ai tham chiếu theo id: chưa
 * cường hoá, không khảm ngọc/tẩy luyện/linh thú, không đang mặc, không đang chào giao dịch.
 */
public final class PostgresBagSortAdapter implements BagSortPort {

    private static final String LOCK_ROWS = "SELECT ii.id, ii.item_template_id, it.code, ii.quantity, it.max_stack,"
            + " it.equip_slot, it.use_effect IS NOT NULL AS usable, it.required_level, ii.enhance_level, ii.locked,"
            + " EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id) AS equipped,"
            + " (ii.enhance_level = 0"
            + " AND NOT EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id)"
            + " AND NOT EXISTS (SELECT 1 FROM item_socket s WHERE s.inventory_item_id = ii.id)"
            + " AND NOT EXISTS (SELECT 1 FROM item_refinement r WHERE r.inventory_item_id = ii.id)"
            + " AND NOT EXISTS (SELECT 1 FROM pet_state p WHERE p.inventory_item_id = ii.id)"
            + " AND " + TradeOfferedQuantity.OF_II + " = 0) AS mergeable"
            + " FROM inventory_item ii JOIN item_template it ON it.id = ii.item_template_id"
            + " WHERE ii.character_id = ? ORDER BY ii.id FOR UPDATE OF ii";

    private static final String PLACE = "UPDATE inventory_item SET quantity = ?, slot_index = ? WHERE id = ?";

    private static final String DELETE = "DELETE FROM inventory_item WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresBagSortAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int sort(long characterId) {
        return JdbcTransactionRunner.call(dataSource, connection -> sortIn(connection, characterId));
    }

    private static int sortIn(Connection connection, long characterId) {
        CharacterInventoryLock.acquire(connection, characterId);
        List<BagSortPolicy.Row> rows = JdbcOperations.query(
                connection,
                LOCK_ROWS,
                rs -> new BagSortPolicy.Row(
                        rs.getLong("id"),
                        rs.getLong("item_template_id"),
                        rs.getString("code"),
                        rs.getInt("quantity"),
                        rs.getInt("max_stack"),
                        Optional.ofNullable(rs.getString("equip_slot")),
                        rs.getBoolean("usable"),
                        rs.getInt("required_level"),
                        rs.getInt("enhance_level"),
                        rs.getBoolean("equipped"),
                        rs.getBoolean("mergeable"),
                        rs.getBoolean("locked")),
                characterId);
        BagSortPolicy.Plan plan = BagSortPolicy.plan(rows);
        for (Long id : plan.deletedIds()) {
            JdbcOperations.update(connection, DELETE, id);
        }
        for (BagSortPolicy.Placement placement : plan.placements()) {
            JdbcOperations.update(connection, PLACE, placement.quantity(), placement.slotIndex(), placement.id());
        }
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "BAG_SORTED",
                "{\"rows\":" + rows.size() + ",\"merged\":" + plan.deletedIds().size() + "}",
                Timestamp.from(Instant.now()));
        return plan.deletedIds().size();
    }
}
