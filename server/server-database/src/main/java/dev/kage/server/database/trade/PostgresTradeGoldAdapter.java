package dev.kage.server.database.trade;

import dev.kage.server.database.DatabaseAccessException;
import dev.kage.trade.domain.Transaction;
import dev.kage.trade.domain.TradeGoldPort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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
        try (PreparedStatement statement = connection.prepareStatement(DEDUCT_IF_ENOUGH)) {
            statement.setLong(1, amount);
            statement.setLong(2, characterId);
            statement.setLong(3, amount);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi trừ gold: characterId=" + characterId + ", amount=" + amount, e);
        }
    }

    @Override
    public void creditGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        try (PreparedStatement statement = connection.prepareStatement(CREDIT)) {
            statement.setLong(1, amount);
            statement.setLong(2, characterId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi cộng gold: characterId=" + characterId + ", amount=" + amount, e);
        }
    }
}
