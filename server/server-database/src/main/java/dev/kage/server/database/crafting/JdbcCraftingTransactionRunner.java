package dev.kage.server.database.crafting;

import java.sql.Connection;
import java.util.function.Consumer;

import javax.sql.DataSource;

import dev.kage.crafting.domain.Transaction;
import dev.kage.crafting.domain.TransactionRunner;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@code feature-crafting}'s {@link TransactionRunner} bằng JDBC thật (ADR-079). */
public final class JdbcCraftingTransactionRunner implements TransactionRunner {

    private final DataSource dataSource;

    public JdbcCraftingTransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void inTransaction(Consumer<Transaction> action) {
        JdbcTransactionRunner.run(dataSource, connection -> action.accept(new JdbcCraftingTransaction(connection)));
    }

    /** Bọc {@link Connection} thật — adapter trong package này ép kiểu về lại. */
    record JdbcCraftingTransaction(Connection connection) implements Transaction {}
}
