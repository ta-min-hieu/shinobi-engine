package dev.kage.server.database.inventory;

import java.sql.Connection;

import dev.kage.inventory.domain.TradeOfferPort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link TradeOfferPort} trên PostgreSQL — đọc {@code trade_offer_item}/{@code
 * trade_session} (V16) từ phía túi đồ, cùng cách {@code PostgresTradeInventoryAdapter} đọc
 * {@code inventory_item} từ phía giao dịch.
 *
 * <p>Đọc thuần, KHÔNG khoá dòng phiên: khoá dòng {@code trade_session} khi đã giữ khoá túi đồ là
 * đảo thứ tự khoá của DATABASE.md §5.1 (giao dịch khoá phiên TRƯỚC túi đồ). Cũng không cần: đặt lời
 * chào lấy khoá túi đồ của người chào, nên lúc câu này chạy mọi lời chào của nhân vật này hoặc đã
 * commit, hoặc chưa bắt đầu.
 */
public final class PostgresInventoryTradeOfferAdapter implements TradeOfferPort {

    // Phiên thi hành/huỷ bị XOÁ (V16, ON DELETE CASCADE sang trade_offer_item) — còn dòng là còn mở.
    // Lọc thêm status cho rõ ý: lời chào chỉ đặt được khi phiên OPEN.
    private static final String IS_OFFERED = """
            SELECT 1 FROM trade_offer_item o
              JOIN trade_session s ON s.id = o.session_id
             WHERE o.offering_character_id = ? AND o.item_id = ? AND s.status = 'OPEN'
             LIMIT 1
            """;

    @Override
    public boolean isOfferedInActiveTrade(Transaction tx, long characterId, long itemId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        return JdbcOperations.queryOne(connection, IS_OFFERED, rs -> Boolean.TRUE, characterId, itemId)
                .isPresent();
    }
}
