package dev.kage.server.database.mail;

import dev.kage.mail.domain.GoldPort;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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
        var connection = ((JdbcTransaction) tx).connection();
        try (PreparedStatement statement = connection.prepareStatement(CREDIT)) {
            statement.setLong(1, amount);
            statement.setLong(2, characterId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi cộng gold từ thư: characterId=" + characterId + ", amount=" + amount, e);
        }
    }
}
