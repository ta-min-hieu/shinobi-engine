package dev.kage.server.database.market;

import java.sql.Connection;

import dev.kage.market.domain.Transaction;

/** {@link Transaction} của feature-market trên một {@link Connection} JDBC đang mở. */
final class JdbcTransaction implements Transaction {

    private final Connection connection;

    JdbcTransaction(Connection connection) {
        this.connection = connection;
    }

    Connection connection() {
        return connection;
    }
}
