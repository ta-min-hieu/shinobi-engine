package dev.kage.server.database.character;

import dev.kage.character.domain.Transaction;
import dev.kage.character.domain.TransactionRunner;
import dev.kage.server.database.JdbcTransactionRunner;
import java.util.function.Consumer;
import javax.sql.DataSource;

/** Hiện thực {@code feature-character}'s {@link TransactionRunner} bằng JDBC thật. */
public final class JdbcCharacterTransactionRunner implements TransactionRunner {

    private final DataSource dataSource;

    public JdbcCharacterTransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void inTransaction(Consumer<Transaction> action) {
        JdbcTransactionRunner.run(dataSource, connection -> action.accept(new JdbcTransaction(connection)));
    }
}
