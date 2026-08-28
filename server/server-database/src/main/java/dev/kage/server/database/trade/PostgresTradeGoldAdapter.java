package dev.kage.server.database.trade;

import java.sql.Connection;

import dev.kage.server.database.GoldLedger;
import dev.kage.trade.domain.TradeGoldPort;
import dev.kage.trade.domain.Transaction;

/**
 * Hiện thực {@link TradeGoldPort} trên PostgreSQL — gold vật lý ở {@code character_stat.gold} (V7).
 *
 * <p>Không còn tự viết SQL: đi qua {@link GoldLedger}, điểm DUY NHẤT đổi cột đó và cũng là chỗ phát
 * {@code GoldChangedEvent} (ADR-035 Decision-3). Một lần {@code ConfirmTrade} đổi vàng của CẢ HAI
 * bên trong cùng transaction — {@link GoldLedger} ghi tạm cả hai và phát cả hai sau khi commit, nên
 * người nhận tiền cũng thấy số mới ngay dù họ không bấm gì.
 *
 * <p>Cả hai chiều cùng một use-case (giao dịch giữa người chơi) nên dùng chung một {@code reasonKey}.
 */
public final class PostgresTradeGoldAdapter implements TradeGoldPort {

    private static final String REASON_TRADE = "gold.reason.trade";

    @Override
    public boolean deductGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        return GoldLedger.deduct(connection, characterId, amount, REASON_TRADE);
    }

    @Override
    public void creditGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        GoldLedger.credit(connection, characterId, amount, REASON_TRADE);
    }
}
