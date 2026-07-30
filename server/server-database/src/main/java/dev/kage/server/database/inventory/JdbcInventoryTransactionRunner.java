package dev.kage.server.database.inventory;

import java.util.function.Consumer;

import javax.sql.DataSource;

import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@code feature-inventory}'s {@link TransactionRunner} bằng JDBC thật. */
public final class JdbcInventoryTransactionRunner implements TransactionRunner {

    private final DataSource dataSource;

    public JdbcInventoryTransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void inTransaction(Consumer<Transaction> action) {
        JdbcTransactionRunner.run(dataSource, connection -> action.accept(new JdbcTransaction(connection)));
    }
}
