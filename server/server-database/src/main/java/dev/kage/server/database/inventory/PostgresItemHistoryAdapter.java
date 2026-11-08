package dev.kage.server.database.inventory;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.inventory.domain.ItemHistoryPort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link ItemHistoryPort} (ADR-232/233). Ứng viên phân giải lọc sơ bộ cùng điều kiện với phân giải từng món
 * (adapter phân giải vẫn kiểm lại từng món dưới khoá); lịch sử cường hoá đọc audit {@code ENHANCE_ITEM} — không bảng
 * mới.
 */
public final class PostgresItemHistoryAdapter implements ItemHistoryPort {

    private static final String CANDIDATES = "SELECT ii.id FROM inventory_item ii"
            + " JOIN item_template it ON it.id = ii.item_template_id"
            + " WHERE ii.character_id = ? AND it.equip_slot IS NOT NULL AND it.required_level <= ? AND NOT ii.locked"
            + " AND NOT EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id)"
            + " AND NOT EXISTS (SELECT 1 FROM item_socket s WHERE s.inventory_item_id = ii.id)"
            + " AND " + TradeOfferedQuantity.OF_II + " = 0"
            + " ORDER BY ii.slot_index";

    private static final String HISTORY = """
            SELECT (a.detail_json::jsonb ->> 'fromLevel')::int AS from_level,
                   (a.detail_json::jsonb ->> 'toLevel')::int AS to_level,
                   (a.detail_json::jsonb ->> 'goldCost')::bigint AS gold_cost,
                   a.detail_json::jsonb ->> 'result' AS result,
                   a.created_at, COALESCE(ii.item_template_id, 0) AS template_id
              FROM audit_log a
              LEFT JOIN inventory_item ii
                     ON ii.id = (a.detail_json::jsonb ->> 'itemId')::bigint AND ii.character_id = a.character_id
             WHERE a.character_id = ? AND a.action = 'ENHANCE_ITEM'
             ORDER BY a.id DESC
             LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresItemHistoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Long> dismantleCandidates(long characterId, int maxRequiredLevel) {
        return JdbcOperations.query(dataSource, CANDIDATES, rs -> rs.getLong(1), characterId, maxRequiredLevel);
    }

    @Override
    public List<EnhanceRecord> enhanceHistory(long characterId, int limit) {
        return JdbcOperations.query(
                dataSource,
                HISTORY,
                rs -> new EnhanceRecord(
                        rs.getLong("template_id"),
                        rs.getInt("from_level"),
                        rs.getInt("to_level"),
                        rs.getLong("gold_cost"),
                        rs.getString("result"),
                        rs.getTimestamp("created_at").toInstant()),
                characterId,
                limit);
    }
}
