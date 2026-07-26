package dev.kage.server.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logic transaction JDBC dùng chung (mở → autocommit=false → chạy → commit; lỗi → rollback).
 *
 * <p>Mỗi feature tự định nghĩa {@code TransactionRunner}/{@code Transaction} riêng trong
 * {@code domain/} (docs/templates/feature-module — domain không phụ thuộc module khác), nên
 * class này KHÔNG implement trực tiếp interface của feature nào — mỗi feature viết một
 * {@code Jdbc<Feature>TransactionRunner} mỏng gọi vào đây, bọc {@link Connection} bằng đúng
 * marker type {@code Transaction} của feature đó. Xem {@code account.JdbcAccountTransactionRunner}.
 *
 * <p>{@link #call} là bản trả về giá trị của {@link #run} — dùng cho adapter tự quản lý transaction
 * thủ công (không qua marker {@code Transaction} của feature, VD {@code PostgresGuildAdapter
 * #createGuild}, {@code PostgresPartyAdapter}) mà cần trả kết quả tính được bên trong transaction.
 * KHÔNG dùng cho case cần rollback có điều kiện MÀ KHÔNG throw (VD {@code
 * PostgresTradeSessionAdapter#replaceOffer} — trả {@code false} vẫn phải rollback dù không có lỗi)
 * — case đó tự quản lý transaction riêng, không ép vào đây.
 */
public final class JdbcTransactionRunner {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcTransactionRunner.class);

    private JdbcTransactionRunner() {
    }

    public static void run(DataSource dataSource, Consumer<Connection> action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            runAction(connection, action);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi transaction JDBC", e);
        }
    }

    public static <T> T call(DataSource dataSource, Function<Connection, T> action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            return callAction(connection, action);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi transaction JDBC", e);
        }
    }

    // PMD AvoidCatchingGenericException: đúng ý — transaction PHẢI rollback khi action ném BẤT KỲ
    // RuntimeException nào (không thể liệt kê hết loại exception nghiệp vụ của caller). Đây là
    // ranh giới transaction, không phải nơi xử lý lỗi nghiệp vụ cụ thể — rethrow ngay sau rollback.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void runAction(Connection connection, Consumer<Connection> action) throws SQLException {
        try {
            action.accept(connection);
            connection.commit();
        } catch (RuntimeException e) {
            rollbackQuietly(connection);
            throw e;
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static <T> T callAction(Connection connection, Function<Connection, T> action) throws SQLException {
        try {
            T result = action.apply(connection);
            connection.commit();
            return result;
        } catch (RuntimeException e) {
            rollbackQuietly(connection);
            throw e;
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            LOG.error("Rollback thất bại", e);
        }
    }
}
