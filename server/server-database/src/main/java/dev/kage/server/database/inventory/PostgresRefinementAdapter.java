package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

import javax.sql.DataSource;

import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.RefinementPolicy;
import dev.kage.inventory.domain.RefinementPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link RefinementPort} (ADR-111, V91). Giao dịch tẩy: khoá túi chung ({@link CharacterInventoryLock},
 * DATABASE.md §5.1) → khoá dòng món (phải đang mặc ở ô có chỉ số) → khoá dòng tẩy hiện có → trừ đá
 * ({@link TemplateQuantityConsumer}, không đủ thì không đổi gì) → ghi đè dòng → audit {@code ITEM_REFINED}.
 */
public final class PostgresRefinementAdapter implements RefinementPort {

    private static final String WORN = """
            SELECT es.slot_type, ii.id, ii.item_template_id, r.line_index, r.stat, r.value
              FROM equipment_slot es
              JOIN inventory_item ii ON ii.id = es.inventory_item_id
              LEFT JOIN item_refinement r ON r.inventory_item_id = ii.id
             WHERE es.character_id = ? AND es.slot_type NOT IN ('MOUNT', 'PET')
             ORDER BY es.slot_type, r.line_index
            """;

    private static final String WORN_BONUS = """
            SELECT r.stat, COALESCE(SUM(r.value), 0)
              FROM equipment_slot es
              JOIN item_refinement r ON r.inventory_item_id = es.inventory_item_id
             WHERE es.character_id = ? AND es.slot_type NOT IN ('MOUNT', 'PET')
             GROUP BY r.stat
            """;

    private static final String LOCK_ITEM = """
            SELECT es.slot_type, it.required_level
              FROM inventory_item ii
              JOIN item_template it ON it.id = ii.item_template_id
              LEFT JOIN equipment_slot es ON es.inventory_item_id = ii.id
             WHERE ii.id = ? AND ii.character_id = ?
               FOR UPDATE OF ii
            """;

    private static final String LINES_FOR_UPDATE = """
            SELECT line_index, stat, value FROM item_refinement WHERE inventory_item_id = ?
             ORDER BY line_index FOR UPDATE
            """;

    private static final String STONE_TEMPLATE = "SELECT id FROM item_template WHERE code = ?";
    private static final String DELETE_LINES = "DELETE FROM item_refinement WHERE inventory_item_id = ?";
    private static final String INSERT_LINE =
            "INSERT INTO item_refinement (inventory_item_id, line_index, stat, value) VALUES (?, ?, ?, ?)";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Dòng món vừa khoá: ô đang mặc ({@code null} = không mặc) + cấp yêu cầu. */
    private record LockedItem(String slot, int requiredLevel) {}

    /** Một dòng của câu {@link #WORN}. */
    private record WornRow(String slot, long itemId, long templateId, Optional<RefinementPolicy.Line> line) {}

    private final DataSource dataSource;
    private final RandomGenerator random;

    public PostgresRefinementAdapter(DataSource dataSource, RandomGenerator random) {
        this.dataSource = dataSource;
        this.random = random;
    }

    @Override
    public List<WornItem> wornItems(long characterId) {
        List<WornRow> rows = JdbcOperations.query(
                dataSource,
                WORN,
                rs -> new WornRow(
                        rs.getString(1),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getString(5) == null
                                ? Optional.empty()
                                : Optional.of(new RefinementPolicy.Line(
                                        rs.getInt(4), RefinementPolicy.Stat.valueOf(rs.getString(5)), rs.getInt(6)))),
                characterId);
        Map<Long, WornRow> heads = new LinkedHashMap<>();
        Map<Long, List<RefinementPolicy.Line>> lines = new LinkedHashMap<>();
        for (WornRow row : rows) {
            heads.putIfAbsent(row.itemId(), row);
            List<RefinementPolicy.Line> itemLines = lines.computeIfAbsent(row.itemId(), id -> new ArrayList<>());
            row.line().ifPresent(itemLines::add);
        }
        List<WornItem> items = new ArrayList<>(heads.size());
        heads.values()
                .forEach(head -> items.add(
                        new WornItem(head.itemId(), head.templateId(), head.slot(), lines.get(head.itemId()))));
        return items;
    }

    @Override
    public EquipmentBonus wornBonusOf(long characterId) {
        List<RefinementPolicy.Line> sums = JdbcOperations.query(
                dataSource,
                WORN_BONUS,
                rs -> new RefinementPolicy.Line(0, RefinementPolicy.Stat.valueOf(rs.getString(1)), rs.getInt(2)),
                characterId);
        return RefinementPolicy.bonusOf(sums);
    }

    @Override
    public Result refine(long characterId, long itemId, int lockedLine) {
        return JdbcTransactionRunner.call(
                dataSource, connection -> refineIn(connection, characterId, itemId, lockedLine));
    }

    private Result refineIn(Connection connection, long characterId, long itemId, int lockedLine) {
        CharacterInventoryLock.acquire(connection, characterId);
        Optional<LockedItem> found = JdbcOperations.queryOne(
                connection, LOCK_ITEM, rs -> new LockedItem(rs.getString(1), rs.getInt(2)), itemId, characterId);
        if (found.isEmpty()) {
            return Result.refused(Outcome.NOT_FOUND);
        }
        LockedItem item = found.get();
        if (item.slot() == null || !RefinementPolicy.refinable(EquipmentSlotType.valueOf(item.slot()))) {
            return Result.refused(Outcome.NOT_REFINABLE);
        }
        List<RefinementPolicy.Line> current = JdbcOperations.query(
                connection,
                LINES_FOR_UPDATE,
                rs -> new RefinementPolicy.Line(
                        rs.getInt(1), RefinementPolicy.Stat.valueOf(rs.getString(2)), rs.getInt(3)),
                itemId);
        if (lockedLine != RefinementPolicy.NO_LOCK && (lockedLine < 0 || lockedLine >= current.size())) {
            return Result.refused(Outcome.INVALID_LOCK);
        }
        long stoneTemplateId = JdbcOperations.queryOne(
                        connection, STONE_TEMPLATE, rs -> rs.getLong(1), RefinementPolicy.STONE_CODE)
                .orElseThrow();
        int cost = RefinementPolicy.stoneCost(lockedLine);
        if (!TemplateQuantityConsumer.consume(connection, characterId, stoneTemplateId, cost)) {
            return Result.refused(Outcome.NOT_ENOUGH_STONES);
        }
        List<RefinementPolicy.Line> next = RefinementPolicy.reroll(current, lockedLine, item.requiredLevel(), random);
        JdbcOperations.update(connection, DELETE_LINES, itemId);
        for (RefinementPolicy.Line line : next) {
            JdbcOperations.update(
                    connection, INSERT_LINE, itemId, line.index(), line.stat().name(), line.value());
        }
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "ITEM_REFINED",
                "{\"itemId\":" + itemId + ",\"stones\":" + cost + ",\"lockedLine\":" + lockedLine + ",\"before\":"
                        + json(current) + ",\"after\":" + json(next) + "}",
                Timestamp.from(Instant.now()));
        return new Result(Outcome.OK, next);
    }

    private static String json(List<RefinementPolicy.Line> lines) {
        StringBuilder out = new StringBuilder("[");
        for (RefinementPolicy.Line line : lines) {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append("{\"stat\":\"")
                    .append(line.stat())
                    .append("\",\"value\":")
                    .append(line.value())
                    .append('}');
        }
        return out.append(']').toString();
    }
}
