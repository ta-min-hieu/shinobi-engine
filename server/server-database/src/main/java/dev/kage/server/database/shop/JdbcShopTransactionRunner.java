package dev.kage.server.database.shop;

import java.util.function.Consumer;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.shop.domain.Transaction;
import dev.kage.shop.domain.TransactionRunner;

/** Hiện thực {@code feature-shop}'s {@link TransactionRunner} bằng JDBC thật. */
public final class JdbcShopTransactionRunner implements TransactionRunner {

    private final DataSource dataSource;

    public JdbcShopTransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void inTransaction(Consumer<Transaction> action) {
        JdbcTransactionRunner.run(dataSource, connection -> action.accept(new JdbcTransaction(connection)));
    }
}
