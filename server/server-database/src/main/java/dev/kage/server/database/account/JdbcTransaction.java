package dev.kage.server.database.account;

import dev.kage.account.domain.Transaction;
import java.sql.Connection;

/** Bọc {@link Connection} thật — adapter trong package này ép kiểu về lại để lấy connection. */
final class JdbcTransaction implements Transaction {

    private final Connection connection;

    JdbcTransaction(Connection connection) {
        this.connection = connection;
    }

    Connection connection() {
        return connection;
    }
}
