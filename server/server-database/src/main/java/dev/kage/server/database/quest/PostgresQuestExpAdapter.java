package dev.kage.server.database.quest;

import dev.kage.quest.domain.ExpPort;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Hiện thực {@link ExpPort} trên PostgreSQL — EXP vật lý ở {@code character.exp} (V2). */
public final class PostgresQuestExpAdapter implements ExpPort {

    private static final String GRANT_EXP = """
            UPDATE character SET exp = exp + ? WHERE id = ?
            """;

    @Override
    public void grantExp(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        try (PreparedStatement statement = connection.prepareStatement(GRANT_EXP)) {
            statement.setLong(1, amount);
            statement.setLong(2, characterId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi cộng EXP: characterId=" + characterId + ", amount=" + amount, e);
        }
    }
}
