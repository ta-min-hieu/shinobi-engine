package dev.kage.server.database.trade;

import java.util.function.Consumer;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.trade.domain.Transaction;
import dev.kage.trade.domain.TransactionRunner;

/** Hiện thực {@code feature-trade}'s {@link TransactionRunner} bằng JDBC thật. */
public final class JdbcTradeTransactionRunner implements TransactionRunner {

    private final DataSource dataSource;

    public JdbcTradeTransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void inTransaction(Consumer<Transaction> action) {
        JdbcTransactionRunner.run(dataSource, connection -> action.accept(new JdbcTransaction(connection)));
    }
}
