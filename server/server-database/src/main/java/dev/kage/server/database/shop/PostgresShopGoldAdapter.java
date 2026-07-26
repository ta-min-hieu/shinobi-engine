package dev.kage.server.database.shop;

import dev.kage.server.database.JdbcOperations;
import dev.kage.shop.domain.GoldPort;
import dev.kage.shop.domain.Transaction;
import java.sql.Connection;

/**
 * Hiện thực {@link GoldPort} trên PostgreSQL — gold vật lý ở {@code character_stat.gold} (V7).
 * Bản sao của {@code dev.kage.server.database.inventory.PostgresGoldAdapter} (cường hoá) — mỗi
 * feature-shop/feature-inventory tự có adapter riêng cho port riêng của mình.
 */
public final class PostgresShopGoldAdapter implements GoldPort {

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
