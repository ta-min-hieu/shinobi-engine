package dev.kage.server.database.shop;

import dev.kage.server.database.DatabaseAccessException;
import dev.kage.shop.domain.GoldPort;
import dev.kage.shop.domain.Transaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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

    @Override
    public void creditGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        try (PreparedStatement statement = connection.prepareStatement(CREDIT)) {
            statement.setLong(1, amount);
            statement.setLong(2, characterId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi cộng gold: characterId=" + characterId + ", amount=" + amount, e);
        }
    }
}
