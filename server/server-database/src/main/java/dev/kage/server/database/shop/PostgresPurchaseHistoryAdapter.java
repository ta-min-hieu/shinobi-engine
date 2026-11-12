package dev.kage.server.database.shop;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.shop.domain.PurchaseHistoryPort;

/** Hiện thực {@link PurchaseHistoryPort} (ADR-288) — audit {@code SHOP_BUY} ({@code BuyItemUseCase} ghi sẵn). */
public final class PostgresPurchaseHistoryAdapter implements PurchaseHistoryPort {

    private static final String RECENT = """
            SELECT (detail_json::jsonb ->> 'itemTemplateId')::bigint AS item_template_id,
                   (detail_json::jsonb ->> 'quantity')::int AS quantity,
                   (detail_json::jsonb ->> 'goldCost')::bigint AS gold_cost,
                   created_at
              FROM audit_log
             WHERE character_id = ? AND action = 'SHOP_BUY'
             ORDER BY id DESC
             LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresPurchaseHistoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Purchase> recent(long characterId, int limit) {
        return JdbcOperations.query(
                dataSource,
                RECENT,
                rs -> new Purchase(
                        rs.getLong("item_template_id"),
                        rs.getInt("quantity"),
                        rs.getLong("gold_cost"),
                        rs.getTimestamp("created_at").toInstant()),
                characterId,
                limit);
    }
}
