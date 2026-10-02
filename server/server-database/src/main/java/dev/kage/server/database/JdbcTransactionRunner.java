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

    // OBSERVABILITY.md §3.3 kage.db.transaction.failed — xem javadoc DbMetricsPort/JdbcOperations
    // cho lý do field tĩnh (cấu hình 1 LẦN lúc khởi động, mặc định NO_OP).
    private static volatile DbMetricsPort metricsPort = DbMetricsPort.NO_OP;

    private JdbcTransactionRunner() {}

    /**
     * Gọi 1 LẦN lúc khởi động ({@code ServerDatabaseBootstrap}) để bật đo {@code
     * kage.db.transaction.failed} thật — xem {@link JdbcOperations#configureMetrics} (cùng port,
     * cấu hình cùng chỗ).
     */
    public static void configureMetrics(DbMetricsPort port) {
        metricsPort = port;
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
    //
    // SQLException cũng phải đi qua nhánh dọn dẹp (sửa 2026-10-02): COMMIT tự nó có thể thất bại (mất
    // kết nối, ràng buộc DEFERRABLE, lỗi tuần tự hoá) và ném SQLException CHECKED. Bản cũ chỉ bắt
    // RuntimeException nên commit hỏng bỏ qua cả việc đếm kage.db.transaction.failed lẫn việc vứt
    // vàng ghi tạm trong GoldLedger — thay đổi đó nằm lại theo Connection mãi mãi, và nếu đối tượng
    // Connection được dùng lại thì bị BÁO ở lần commit sau (JdbcTransactionRunnerCommitFailureTest).
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void runAction(Connection connection, Consumer<Connection> action) throws SQLException {
        try {
            action.accept(connection);
            connection.commit();
        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(connection);
            throw e;
        }
        GoldLedger.publishCommitted(connection);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static <T> T callAction(Connection connection, Function<Connection, T> action) throws SQLException {
        T result;
        try {
            result = action.apply(connection);
            connection.commit();
        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(connection);
            throw e;
        }
        GoldLedger.publishCommitted(connection);
        return result;
    }

    private static void rollbackQuietly(Connection connection) {
        // Đếm NGAY khi action ném RuntimeException (transaction coi như thất bại), bất kể bản thân
        // rollback() có thành công hay không — OBSERVABILITY.md §3.3 kage.db.transaction.failed.
        metricsPort.recordTransactionFailed();
        // Vàng có thể đã bị trừ trong transaction này rồi mới abort (VD BuyItemUseCase: trừ tiền
        // xong phát hiện túi đầy) — vứt thay đổi đã ghi tạm, KHÔNG báo số dư chưa từng tồn tại.
        GoldLedger.discardPending(connection);
        try {
            connection.rollback();
        } catch (SQLException e) {
            LOG.error("Rollback thất bại", e);
        }
    }
}
