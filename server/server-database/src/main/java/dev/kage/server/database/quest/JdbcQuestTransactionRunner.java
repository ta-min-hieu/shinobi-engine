package dev.kage.server.database.quest;

import java.util.function.Consumer;

import javax.sql.DataSource;

import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@code feature-quest}'s {@link TransactionRunner} bằng JDBC thật. */
public final class JdbcQuestTransactionRunner implements TransactionRunner {

    private final DataSource dataSource;

    public JdbcQuestTransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void inTransaction(Consumer<Transaction> action) {
        JdbcTransactionRunner.run(dataSource, connection -> action.accept(new JdbcTransaction(connection)));
    }
}
