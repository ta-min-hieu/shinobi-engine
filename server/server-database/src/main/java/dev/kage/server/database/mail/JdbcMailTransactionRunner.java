package dev.kage.server.database.mail;

import dev.kage.mail.domain.Transaction;
import dev.kage.mail.domain.TransactionRunner;
import dev.kage.server.database.JdbcTransactionRunner;
import java.util.function.Consumer;
import javax.sql.DataSource;

/** Hiện thực {@code feature-mail}'s {@link TransactionRunner} bằng JDBC thật. */
public final class JdbcMailTransactionRunner implements TransactionRunner {

    private final DataSource dataSource;

    public JdbcMailTransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void inTransaction(Consumer<Transaction> action) {
        JdbcTransactionRunner.run(dataSource, connection -> action.accept(new JdbcTransaction(connection)));
    }
}
