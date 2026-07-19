package dev.kage.server.database.account;

import dev.kage.account.domain.Transaction;
import dev.kage.account.domain.TransactionRunner;
import dev.kage.server.database.JdbcTransactionRunner;
import java.util.function.Consumer;
import javax.sql.DataSource;

/** Hiện thực {@code feature-account}'s {@link TransactionRunner} bằng JDBC thật. */
public final class JdbcAccountTransactionRunner implements TransactionRunner {

    private final DataSource dataSource;

    public JdbcAccountTransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void inTransaction(Consumer<Transaction> action) {
        JdbcTransactionRunner.run(dataSource, connection -> action.accept(new JdbcTransaction(connection)));
    }
}
