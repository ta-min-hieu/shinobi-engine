package dev.kage.server.database.trade;

import dev.kage.server.database.JdbcOperations;
import dev.kage.trade.domain.Transaction;
import dev.kage.trade.domain.TradeGoldPort;
import java.sql.Connection;

/**
 * Hiện thực {@link TradeGoldPort} trên PostgreSQL — gold vật lý ở {@code character_stat.gold}
 * (V7). Bản sao ý tưởng {@code dev.kage.server.database.shop.PostgresShopGoldAdapter}.
 */
public final class PostgresTradeGoldAdapter implements TradeGoldPort {

    private static final String DEDUCT_IF_ENOUGH = """
            UPDATE character_stat SET gold = gold - ? WHERE character_id = ? AND gold >= ?
            """;

    private static final String CREDIT = """
            UPDATE character_stat SET gold = gold + ? WHERE character_id = ?
            """;

    @Override
    public boolean deductGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        return JdbcOperations.update(connection, DEDUCT_IF_ENOUGH, amount, characterId, amount) > 0;
    }

    @Override
    public void creditGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        JdbcOperations.update(connection, CREDIT, amount, characterId);
    }
}
