package dev.kage.server.database.inventory;

import dev.kage.inventory.domain.GoldPort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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
        var connection = ((JdbcTransaction) tx).connection();
        try (PreparedStatement statement = connection.prepareStatement(DEDUCT_IF_ENOUGH)) {
            statement.setLong(1, amount);
            statement.setLong(2, characterId);
            statement.setLong(3, amount);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi trừ gold: characterId=" + characterId + ", amount=" + amount, e);
        }
    }
}
