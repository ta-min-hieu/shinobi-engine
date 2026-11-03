package dev.kage.server.database.inventory;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.inventory.domain.GemCombinePort;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link GemCombinePort} (ADR-161, V129). Thứ tự trong transaction: khoá túi → trừ ngọc (đường "nguyên
 * liệu": không lấy chồng đã khoá — ADR-159) → trừ vàng → thêm viên mới → audit. Bước nào hỏng thì ném {@link Abort} để
 * rollback cả lần ghép.
 */
public final class PostgresGemCombineAdapter implements GemCombinePort {

    private static final String GOLD_REASON = "gold.reason.gem_combine";

    private static final String RECIPES = """
            SELECT r.source_item_template_id, r.result_item_template_id, r.source_quantity, r.gold_fee,
                   COALESCE((SELECT SUM(ii.quantity) FROM inventory_item ii
                              WHERE ii.character_id = ? AND ii.item_template_id = r.source_item_template_id
                                AND ii.enhance_level = 0 AND NOT ii.locked), 0)
              FROM gem_combine_recipe r
             ORDER BY r.sort_order
            """;

    private static final String RECIPE = """
            SELECT result_item_template_id, source_quantity, gold_fee
              FROM gem_combine_recipe WHERE source_item_template_id = ?
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Công thức của một viên nguồn. */
    private record Rule(long resultItemTemplateId, int sourceQuantity, long goldFee) {}

    private final DataSource dataSource;

    public PostgresGemCombineAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Recipe> recipes(long characterId) {
        return JdbcOperations.query(
                dataSource,
                RECIPES,
                rs -> new Recipe(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getLong(4), (int)
                        Math.min(Integer.MAX_VALUE, rs.getLong(5))),
                characterId);
    }

    @Override
    public Outcome combine(long characterId, long sourceItemTemplateId) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource, connection -> combineIn(connection, characterId, sourceItemTemplateId));
        } catch (Abort abort) {
            return abort.outcome;
        }
    }

    private static Outcome combineIn(Connection connection, long characterId, long sourceItemTemplateId) {
        CharacterInventoryLock.acquire(connection, characterId);
        Rule rule = JdbcOperations.queryOne(
                        connection,
                        RECIPE,
                        rs -> new Rule(rs.getLong(1), rs.getInt(2), rs.getLong(3)),
                        sourceItemTemplateId)
                .orElseThrow(() -> new Abort(Outcome.UNKNOWN_RECIPE));
        if (!TemplateQuantityConsumer.consumeUnlocked(
                connection, characterId, new ItemLine(sourceItemTemplateId, rule.sourceQuantity(), 0))) {
            throw new Abort(Outcome.NOT_ENOUGH_GEMS);
        }
        if (!GoldLedger.deduct(connection, characterId, rule.goldFee(), GOLD_REASON)) {
            throw new Abort(Outcome.NOT_ENOUGH_GOLD);
        }
        long result = rule.resultItemTemplateId();
        if (!InventoryStackMerge.mergeIntoExistingStack(connection, characterId, result, 1)
                && !InventoryStackMerge.insertRespectingMaxStack(connection, characterId, result, 1)) {
            throw new Abort(Outcome.BAG_FULL);
        }
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "GEM_COMBINED",
                "{\"source\":" + sourceItemTemplateId + ",\"result\":" + result + ",\"quantity\":"
                        + rule.sourceQuantity() + ",\"gold\":" + rule.goldFee() + "}",
                Timestamp.from(Instant.now()));
        return Outcome.OK;
    }

    /** Từ chối giữa transaction — ném để rollback. */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Outcome outcome;

        Abort(Outcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
