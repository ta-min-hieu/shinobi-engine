package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.inventory.domain.JunkSalePort;
import dev.kage.server.database.GoldCapExceededException;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link JunkSalePort} (ADR-170, V136). Cùng điều kiện "bán được" với bán lẻ ở cửa hàng: chưa cường hoá,
 * không khảm/tẩy luyện/linh thú, không đang mặc, không đang chào giao dịch — thêm: không khoá (ADR-159). Giá thu mua
 * lấy từ {@code shop_item.sell_price}.
 */
public final class PostgresJunkSaleAdapter implements JunkSalePort {

    private static final String GOLD_REASON = "gold.reason.sell_junk";

    private static final String SELLABLE_ROWS = "SELECT ii.id, ii.item_template_id, ii.quantity, s.sell_price"
            + " FROM inventory_item ii"
            + " JOIN item_template it ON it.id = ii.item_template_id"
            + " JOIN shop_item s ON s.item_template_id = it.id"
            + " WHERE ii.character_id = ? AND it.junk AND s.sell_price > 0 AND ii.enhance_level = 0 AND NOT ii.locked"
            + " AND NOT EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id)"
            + " AND NOT EXISTS (SELECT 1 FROM item_socket x WHERE x.inventory_item_id = ii.id)"
            + " AND NOT EXISTS (SELECT 1 FROM item_refinement r WHERE r.inventory_item_id = ii.id)"
            + " AND NOT EXISTS (SELECT 1 FROM pet_state p WHERE p.inventory_item_id = ii.id)"
            + " AND " + TradeOfferedQuantity.OF_II + " = 0"
            + " ORDER BY ii.id";

    private static final String DELETE = "DELETE FROM inventory_item WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Một chồng bán được. */
    private record Row(long id, long itemTemplateId, int quantity, long priceEach) {}

    private final DataSource dataSource;

    public PostgresJunkSaleAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Line> preview(long characterId) {
        return group(JdbcOperations.query(dataSource, SELLABLE_ROWS, PostgresJunkSaleAdapter::row, characterId));
    }

    @Override
    public Sold sellAll(long characterId, Instant at) {
        try {
            return JdbcTransactionRunner.call(dataSource, connection -> sellIn(connection, characterId, at));
        } catch (GoldCapExceededException capped) {
            return new Sold(List.of(), 0L, true);
        }
    }

    private static Sold sellIn(Connection connection, long characterId, Instant at) {
        CharacterInventoryLock.acquire(connection, characterId);
        List<Row> rows = JdbcOperations.query(
                connection, SELLABLE_ROWS + " FOR UPDATE OF ii", PostgresJunkSaleAdapter::row, characterId);
        if (rows.isEmpty()) {
            return new Sold(List.of(), 0L, false);
        }
        long gold = 0L;
        for (Row row : rows) {
            JdbcOperations.update(connection, DELETE, row.id());
            gold = Math.addExact(gold, Math.multiplyExact(row.priceEach(), row.quantity()));
        }
        GoldLedger.credit(connection, characterId, gold, GOLD_REASON);
        List<Line> lines = group(rows);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "JUNK_SOLD",
                "{\"stacks\":" + rows.size() + ",\"gold\":" + gold + "}",
                Timestamp.from(at));
        return new Sold(lines, gold, false);
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getLong(4));
    }

    private static List<Line> group(List<Row> rows) {
        Map<Long, Line> lines = new LinkedHashMap<>();
        for (Row row : rows) {
            lines.merge(
                    row.itemTemplateId(),
                    new Line(row.itemTemplateId(), row.quantity(), row.priceEach()),
                    (a, b) -> new Line(a.itemTemplateId(), a.quantity() + b.quantity(), a.priceEach()));
        }
        return new ArrayList<>(lines.values());
    }
}
