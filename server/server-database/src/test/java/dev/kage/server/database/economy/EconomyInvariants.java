package dev.kage.server.database.economy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Bất biến bảo toàn kinh tế, đọc thẳng DB SAU KHI mọi luồng đã dừng (không bất biến nào ở đây được
 * phép chỉ "gần đúng" giữa chừng — bài kiểm chỉ gọi vào đây lúc tĩnh, nên không bao giờ flaky vì
 * thời điểm đọc).
 *
 * <p><b>Sổ vàng.</b> Bốn đường đổi {@code character_stat.gold} (cửa hàng, cường hoá, thư, giao dịch)
 * đều đi qua {@code GoldLedger} và ghi {@code audit_log} trong CÙNG transaction: {@code SHOP_BUY.goldCost},
 * {@code SHOP_SELL.goldEarned}, {@code ENHANCE_ITEM.goldCost}, {@code MAIL_SEND.goldAmount},
 * {@code MAIL_CLAIM.goldAmount}, {@code TRADE_EXECUTED.goldReceived − goldGiven}. Nên vàng TỪNG nhân
 * vật phải bằng đúng vàng ban đầu cộng tổng sổ của chính họ — chặt hơn hẳn "tổng toàn server".
 *
 * <p>Mỗi phương thức trả danh sách vi phạm (rỗng = đạt), không ném — để một lần chạy báo HẾT những
 * gì sai thay vì dừng ở cái đầu tiên.
 */
final class EconomyInvariants {

    /** Sức chứa túi — PLACEHOLDER trùng {@code DEFAULT_CAPACITY} của các adapter túi đồ. */
    private static final int BAG_CAPACITY = 20;

    private static final String JSON = "a.detail_json::jsonb->>";

    private static final String AUDIT_GOLD_DELTA = "CASE a.action"
            + " WHEN 'SHOP_BUY' THEN -(" + JSON + "'goldCost')::bigint"
            + " WHEN 'SHOP_SELL' THEN (" + JSON + "'goldEarned')::bigint"
            + " WHEN 'ENHANCE_ITEM' THEN -(" + JSON + "'goldCost')::bigint"
            + " WHEN 'MAIL_SEND' THEN -(" + JSON + "'goldAmount')::bigint"
            + " WHEN 'MAIL_CLAIM' THEN (" + JSON + "'goldAmount')::bigint"
            + " WHEN 'TRADE_EXECUTED' THEN (" + JSON + "'goldReceived')::bigint - (" + JSON + "'goldGiven')::bigint"
            + " ELSE 0 END";

    private static final String GOLD_VS_LEDGER = "SELECT c.id, cs.gold, COALESCE(SUM(" + AUDIT_GOLD_DELTA + "), 0)"
            + " FROM character c JOIN character_stat cs ON cs.character_id = c.id"
            + " LEFT JOIN audit_log a ON a.character_id = c.id"
            + " WHERE c.account_id = ? GROUP BY c.id, cs.gold ORDER BY c.id";

    // Vàng chỉ SINH ở bán hàng và chỉ MẤT ở mua hàng/cường hoá; thư và giao dịch chỉ chuyển chỗ.
    // Vàng trong thư chưa nhận là vàng đang "ký quỹ" — vẫn tồn tại, chỉ chưa thuộc túi ai.
    private static final String SYSTEM_GOLD = "SELECT"
            + " (SELECT COALESCE(SUM(cs.gold), 0) FROM character_stat cs JOIN character c ON c.id = cs.character_id"
            + "   WHERE c.account_id = ?)"
            + " + (SELECT COALESCE(SUM(m.gold_amount), 0) FROM mail m WHERE m.claimed_at IS NULL),"
            + " (SELECT COALESCE(SUM(CASE a.action WHEN 'SHOP_SELL' THEN (" + JSON + "'goldEarned')::bigint"
            + "   WHEN 'SHOP_BUY' THEN -(" + JSON + "'goldCost')::bigint"
            + "   WHEN 'ENHANCE_ITEM' THEN -(" + JSON + "'goldCost')::bigint ELSE 0 END), 0)"
            + "   FROM audit_log a JOIN character c ON c.id = a.character_id WHERE c.account_id = ?)";

    private static final String QUANTITY_BY_TEMPLATE = "SELECT t.id,"
            + " COALESCE((SELECT SUM(ii.quantity) FROM inventory_item ii JOIN character c ON c.id = ii.character_id"
            + "   WHERE c.account_id = ? AND ii.item_template_id = t.id), 0)"
            + " + COALESCE((SELECT SUM(mi.quantity) FROM mail_item mi JOIN mail m ON m.id = mi.mail_id"
            + "   WHERE m.claimed_at IS NULL AND mi.item_template_id = t.id), 0)"
            + " FROM item_template t ORDER BY t.id";

    private static final String BAD_ROWS = "SELECT ii.id, ii.character_id, ii.quantity, it.max_stack"
            + " FROM inventory_item ii JOIN item_template it ON it.id = ii.item_template_id"
            + " WHERE ii.quantity <= 0 OR ii.quantity > it.max_stack";

    private static final String OVERFULL_BAGS =
            "SELECT character_id, COUNT(*) FROM inventory_item" + " GROUP BY character_id HAVING COUNT(*) > ?";

    // Món đang mặc phải là của CHÍNH người mặc và đúng loại ô — một món rời túi qua bán/thư/giao dịch
    // trong lúc đang mặc sẽ lộ ra ở đây (người cũ còn ô trỏ vào dòng của người mới).
    private static final String BAD_EQUIPMENT = "SELECT es.character_id, ii.character_id, es.inventory_item_id"
            + " FROM equipment_slot es"
            + " LEFT JOIN inventory_item ii ON ii.id = es.inventory_item_id"
            + " LEFT JOIN item_template it ON it.id = ii.item_template_id"
            + " WHERE es.inventory_item_id IS NOT NULL"
            + "   AND (ii.id IS NULL OR ii.character_id <> es.character_id"
            + "        OR it.equip_slot IS DISTINCT FROM es.slot_type)";

    // Trang bị (max_stack = 1) chỉ đổi cấp qua cường hoá, và mỗi lần cường hoá ghi toLevel — cấp
    // hiện tại phải là toLevel của lần ghi CUỐI cho chính dòng đó (0 nếu chưa từng cường hoá).
    private static final String ENHANCE_DRIFT = "SELECT ii.id, ii.enhance_level, COALESCE((SELECT ("
            + JSON + "'toLevel')::int FROM audit_log a WHERE a.action = 'ENHANCE_ITEM'"
            + "   AND (" + JSON + "'itemId')::bigint = ii.id ORDER BY a.id DESC LIMIT 1), 0)"
            + " FROM inventory_item ii JOIN item_template it ON it.id = ii.item_template_id"
            + " WHERE it.max_stack = 1";

    private static final String SESSIONS_PER_CHARACTER = "SELECT p, COUNT(*) FROM ("
            + " SELECT requester_character_id AS p FROM trade_session"
            + " UNION ALL SELECT target_character_id FROM trade_session) s GROUP BY p HAVING COUNT(*) > 1";

    private static final String BOUGHT_BY_TEMPLATE = "SELECT (" + JSON + "'itemTemplateId')::bigint," + " SUM((" + JSON
            + "'quantity')::bigint) FROM audit_log a WHERE a.action = 'SHOP_BUY' GROUP BY 1";

    private static final String USED_BY_TEMPLATE = "SELECT (" + JSON + "'itemTemplateId')::bigint, COUNT(*)"
            + " FROM audit_log a WHERE a.action = 'USE_ITEM' GROUP BY 1";

    private static final String SOLD_QUANTITY =
            "SELECT COALESCE(SUM((" + JSON + "'quantity')::bigint), 0) FROM audit_log a WHERE a.action = 'SHOP_SELL'";

    private final EconomyStressWorld world;
    private final Map<Long, LongAdder> committedGoldDeltas;

    EconomyInvariants(EconomyStressWorld world, Map<Long, LongAdder> committedGoldDeltas) {
        this.world = world;
        this.committedGoldDeltas = committedGoldDeltas;
    }

    /** Mọi bất biến khẳng định được lúc tĩnh (giữa các vòng và ở cuối). */
    List<String> checkAll() {
        List<String> violations = new ArrayList<>();
        violations.addAll(goldMatchesLedgerPerCharacter());
        violations.addAll(systemGoldIsConserved());
        violations.addAll(itemQuantitiesAreConserved());
        violations.addAll(auditAgreesWithDriver());
        violations.addAll(rowsAreWellFormed());
        violations.addAll(equipmentIsOwnedAndFits());
        violations.addAll(enhanceLevelsMatchAudit());
        violations.addAll(eachCharacterInAtMostOneTrade());
        return violations;
    }

    private List<String> goldMatchesLedgerPerCharacter() {
        List<String> violations = new ArrayList<>();
        for (long[] row : query(GOLD_VS_LEDGER, world.accountId())) {
            long characterId = row[0];
            long gold = row[1];
            long ledgerDelta = row[2];
            if (gold < 0) {
                violations.add("vàng âm: nhân vật %d có %d".formatted(characterId, gold));
            }
            if (gold != world.initialGoldEach() + ledgerDelta) {
                violations.add("vàng lệch sổ audit: nhân vật %d có %d, sổ nói %d"
                        .formatted(characterId, gold, world.initialGoldEach() + ledgerDelta));
            }
            LongAdder published = committedGoldDeltas.get(characterId);
            long publishedDelta = published == null ? 0L : published.sum();
            if (gold != world.initialGoldEach() + publishedDelta) {
                violations.add("vàng lệch GoldChangedEvent đã phát: nhân vật %d có %d, sự kiện cộng dồn ra %d"
                        .formatted(characterId, gold, world.initialGoldEach() + publishedDelta));
            }
        }
        return violations;
    }

    private List<String> systemGoldIsConserved() {
        long[] row = query(SYSTEM_GOLD, world.accountId(), world.accountId()).get(0);
        long expected = world.initialGoldEach() * world.characters().size() + row[1];
        return row[0] == expected
                ? List.of()
                : List.of("tổng vàng (túi + thư chưa nhận) = %d, kỳ vọng ban đầu + bán − mua − cường hoá = %d"
                        .formatted(row[0], expected));
    }

    private List<String> itemQuantitiesAreConserved() {
        List<String> violations = new ArrayList<>();
        for (long[] row : query(QUANTITY_BY_TEMPLATE, world.accountId())) {
            long templateId = row[0];
            long expected = world.initialQuantityByTemplate().getOrDefault(templateId, 0L)
                    + world.tally().netCreated(templateId);
            if (row[1] != expected) {
                violations.add("số lượng loại %s (túi + thư chưa nhận) = %d, kỳ vọng ban đầu + tạo − huỷ = %d"
                        .formatted(world.catalog().template(templateId).code(), row[1], expected));
            }
        }
        return violations;
    }

    private List<String> auditAgreesWithDriver() {
        List<String> violations = new ArrayList<>();
        Map<Long, Long> bought = toMap(query(BOUGHT_BY_TEMPLATE));
        Map<Long, Long> used = toMap(query(USED_BY_TEMPLATE));
        for (long templateId : world.catalog().templates().keySet()) {
            long auditBought = bought.getOrDefault(templateId, 0L);
            if (auditBought != world.tally().boughtCount(templateId)) {
                violations.add("SHOP_BUY loại %d: audit %d, use case báo OK %d"
                        .formatted(templateId, auditBought, world.tally().boughtCount(templateId)));
            }
            long auditUsed = used.getOrDefault(templateId, 0L);
            if (auditUsed != world.tally().usedCount(templateId)) {
                violations.add("USE_ITEM loại %d: audit %d, use case báo OK %d"
                        .formatted(templateId, auditUsed, world.tally().usedCount(templateId)));
            }
        }
        long auditSold = query(SOLD_QUANTITY).get(0)[0];
        if (auditSold != world.tally().soldQuantity()) {
            violations.add("SHOP_SELL: audit %d món, use case báo OK %d món"
                    .formatted(auditSold, world.tally().soldQuantity()));
        }
        return violations;
    }

    private List<String> rowsAreWellFormed() {
        List<String> violations = new ArrayList<>();
        for (long[] row : query(BAD_ROWS)) {
            violations.add("dòng túi %d (nhân vật %d) quantity=%d ngoài (0, max_stack=%d]"
                    .formatted(row[0], row[1], row[2], row[3]));
        }
        for (long[] row : query(OVERFULL_BAGS, BAG_CAPACITY)) {
            violations.add("túi nhân vật %d có %d dòng > sức chứa %d".formatted(row[0], row[1], BAG_CAPACITY));
        }
        return violations;
    }

    private List<String> equipmentIsOwnedAndFits() {
        List<String> violations = new ArrayList<>();
        for (long[] row : query(BAD_EQUIPMENT)) {
            violations.add("ô trang bị của nhân vật %d trỏ vào dòng %d của chủ %d (hoặc sai loại ô/đã mất)"
                    .formatted(row[0], row[2], row[1]));
        }
        return violations;
    }

    private List<String> enhanceLevelsMatchAudit() {
        List<String> violations = new ArrayList<>();
        for (long[] row : query(ENHANCE_DRIFT)) {
            if (row[1] != row[2]) {
                violations.add("dòng %d cấp +%d, lần ENHANCE_ITEM cuối ghi +%d".formatted(row[0], row[1], row[2]));
            }
        }
        return violations;
    }

    private List<String> eachCharacterInAtMostOneTrade() {
        List<String> violations = new ArrayList<>();
        for (long[] row : query(SESSIONS_PER_CHARACTER)) {
            violations.add("nhân vật %d nằm trong %d phiên giao dịch".formatted(row[0], row[1]));
        }
        return violations;
    }

    // ---------------------------------------------------------------- JDBC test-only

    long single(String sql, Object... params) {
        return query(sql, params).get(0)[0];
    }

    private List<long[]> query(String sql, Object... params) {
        try (Connection connection = world.dataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                int columns = rs.getMetaData().getColumnCount();
                List<long[]> rows = new ArrayList<>();
                while (rs.next()) {
                    long[] row = new long[columns];
                    for (int c = 0; c < columns; c++) {
                        row[c] = rs.getLong(c + 1);
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }

    private static Map<Long, Long> toMap(List<long[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (long[] row : rows) {
            map.put(row[0], row[1]);
        }
        return map;
    }
}
