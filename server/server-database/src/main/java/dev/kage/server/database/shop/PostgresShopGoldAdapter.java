package dev.kage.server.database.shop;

import java.sql.Connection;

import dev.kage.server.database.GoldLedger;
import dev.kage.shop.domain.GoldPort;
import dev.kage.shop.domain.Transaction;

/**
 * Hiện thực {@link GoldPort} trên PostgreSQL — gold vật lý ở {@code character_stat.gold} (V7).
 *
 * <p>Không còn tự viết SQL (trước đây là bản sao câu UPDATE của
 * {@code dev.kage.server.database.inventory.PostgresGoldAdapter}): mọi thay đổi vàng đi qua
 * {@link GoldLedger}, điểm DUY NHẤT đổi cột đó và cũng là chỗ phát {@code GoldChangedEvent}
 * (ADR-035 Decision-3).
 *
 * <p>Hai chiều tiền ở đây tương ứng đúng hai use-case: trừ = mua, cộng = bán.
 */
public final class PostgresShopGoldAdapter implements GoldPort {

    private static final String REASON_BUY = "gold.reason.buy_item";
    private static final String REASON_SELL = "gold.reason.sell_item";

    @Override
    public boolean deductGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        return GoldLedger.deduct(connection, characterId, amount, REASON_BUY);
    }

    @Override
    public void creditGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        GoldLedger.credit(connection, characterId, amount, REASON_SELL);
    }
}
