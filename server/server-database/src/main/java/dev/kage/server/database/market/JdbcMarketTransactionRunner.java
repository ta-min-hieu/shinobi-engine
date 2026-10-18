package dev.kage.server.database.market;

import java.util.function.Consumer;

import javax.sql.DataSource;

import dev.kage.market.domain.Transaction;
import dev.kage.market.domain.TransactionRunner;
import dev.kage.server.database.JdbcTransactionRunner;

/** {@link TransactionRunner} của feature-market — cùng khuôn mọi feature (một connection, commit/rollback). */
public final class JdbcMarketTransactionRunner implements TransactionRunner {

    private final DataSource dataSource;

    public JdbcMarketTransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void inTransaction(Consumer<Transaction> action) {
        JdbcTransactionRunner.run(dataSource, connection -> action.accept(new JdbcTransaction(connection)));
    }
}
