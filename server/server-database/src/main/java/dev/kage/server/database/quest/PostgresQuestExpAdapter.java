package dev.kage.server.database.quest;

import dev.kage.quest.domain.ExpPort;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import java.sql.Connection;

/** Hiện thực {@link ExpPort} trên PostgreSQL — EXP vật lý ở {@code character.exp} (V2). */
public final class PostgresQuestExpAdapter implements ExpPort {

    private static final String GRANT_EXP = """
            UPDATE character SET exp = exp + ? WHERE id = ?
            """;

    @Override
    public void grantExp(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        JdbcOperations.update(connection, GRANT_EXP, amount, characterId);
    }
}
