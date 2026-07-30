package dev.kage.server.database.inventory;

import java.sql.Connection;

import dev.kage.inventory.domain.GoldPort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link GoldPort} trên PostgreSQL — gold vật lý ở {@code character_stat.gold} (V7).
 * 1 câu UPDATE có điều kiện, KHÔNG đọc-rồi-ghi (tránh race giữa 2 request cường hoá đồng thời).
 */
public final class PostgresGoldAdapter implements GoldPort {

    private static final String DEDUCT_IF_ENOUGH = """
            UPDATE character_stat SET gold = gold - ? WHERE character_id = ? AND gold >= ?
            """;

    @Override
    public boolean deductGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        return JdbcOperations.update(connection, DEDUCT_IF_ENOUGH, amount, characterId, amount) > 0;
    }
}
