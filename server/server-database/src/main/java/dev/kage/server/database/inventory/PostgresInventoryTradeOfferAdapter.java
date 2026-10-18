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
 * commit, hoặc chưa bắt đầu. Biểu thức dùng chung ở {@link TradeOfferedQuantity}.
 */
public final class PostgresInventoryTradeOfferAdapter implements TradeOfferPort {

    // Phiên thi hành/huỷ bị XOÁ (V16, ON DELETE CASCADE sang trade_offer_item) — còn dòng là còn mở.
    // Dòng túi đồ không tồn tại / không thuộc characterId ⇒ không có dòng nào ⇒ 0.
    private static final String OFFERED_QUANTITY = "SELECT " + TradeOfferedQuantity.OF_II
            + " AS offered FROM inventory_item ii WHERE ii.id = ? AND ii.character_id = ?";

    @Override
    public int offeredQuantity(Transaction tx, long characterId, long itemId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        return JdbcOperations.queryOne(connection, OFFERED_QUANTITY, rs -> rs.getInt("offered"), itemId, characterId)
                .orElse(0);
    }
}
