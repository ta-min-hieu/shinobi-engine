package dev.kage.server.database.inventory;

import java.sql.Connection;

import dev.kage.inventory.domain.Transaction;

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
