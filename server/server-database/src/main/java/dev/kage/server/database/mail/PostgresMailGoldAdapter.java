package dev.kage.server.database.mail;

import java.sql.Connection;

import dev.kage.mail.domain.GoldPort;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link GoldPort} trên PostgreSQL — gold vật lý ở {@code character_stat.gold} (V7).
 * Bản sao của {@code dev.kage.server.database.shop.PostgresShopGoldAdapter}, chỉ giữ chiều CỘNG.
 */
public final class PostgresMailGoldAdapter implements GoldPort {

    private static final String CREDIT = """
            UPDATE character_stat SET gold = gold + ? WHERE character_id = ?
            """;

    @Override
    public void creditGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        JdbcOperations.update(connection, CREDIT, amount, characterId);
    }
}
