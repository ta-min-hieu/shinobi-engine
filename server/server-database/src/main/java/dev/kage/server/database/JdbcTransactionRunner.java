package dev.kage.server.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
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

    /** SQLSTATE Postgres trả cho transaction bị chọn huỷ để phá deadlock. */
    static final String DEADLOCK_SQL_STATE = "40P01";

    /** Số lần chạy LẠI tối đa sau lần đầu — xem {@link #call}. */
    static final int MAX_DEADLOCK_RETRIES = 3;

    private static final long DEADLOCK_BACKOFF_MIN_MILLIS = 10L;
    private static final long DEADLOCK_BACKOFF_MAX_MILLIS = 50L;

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

    /**
     * Chạy {@code action} trong một transaction. Transaction bị Postgres huỷ vì DEADLOCK (SQLSTATE
     * {@value #DEADLOCK_SQL_STATE}) thì chạy LẠI từ đầu, tối đa {@value #MAX_DEADLOCK_RETRIES} lần, mỗi lần
     * chờ một khoảng ngẫu nhiên — xem {@link #call}.
     */
    // PMD AvoidCatchingGenericException: chỉ để SOI chuỗi nguyên nhân tìm SQLSTATE deadlock — không phải
    // deadlock thì ném lại nguyên trạng (adapter có thể bọc SQLException trong kiểu runtime bất kỳ).
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public static void run(DataSource dataSource, Consumer<Connection> action) {
        for (int attempt = 0; ; attempt++) {
            try {
                runOnce(dataSource, action);
                return;
            } catch (RuntimeException e) {
                rethrowUnlessRetryableDeadlock(e, attempt);
            }
        }
    }

    /**
     * Bản trả giá trị của {@link #run}, cùng luật thử lại.
     *
     * <p><b>Vì sao thử lại deadlock</b> (chủ dự án chốt 2026-10-14, docs/README.md dòng 45(ii)): Postgres
     * phá deadlock bằng cách huỷ MỘT trong hai transaction — bên bị chọn không làm gì sai, chỉ xui. Trước
     * đó lỗi đó đi thẳng lên thành 500 cho người chơi. Rollback đã trả mọi thứ về như chưa chạy (kể cả vàng
     * ghi tạm trong {@link GoldLedger} và khoá idempotency), nên chạy lại là an toàn — với điều kiện
     * {@code action} chỉ làm việc với DB, đúng luật của mọi action đi qua đây. Ba lần và khoảng chờ
     * {@value #DEADLOCK_BACKOFF_MIN_MILLIS}–{@value #DEADLOCK_BACKOFF_MAX_MILLIS} ms nhân số lần là con số
     * tôi chọn: deadlock thật hiếm, và hết ba lần thì lỗi vẫn được ném như cũ.
     */
    // PMD AvoidCatchingGenericException: chỉ để SOI chuỗi nguyên nhân tìm SQLSTATE deadlock — không phải
    // deadlock thì ném lại nguyên trạng (adapter có thể bọc SQLException trong kiểu runtime bất kỳ).
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public static <T> T call(DataSource dataSource, Function<Connection, T> action) {
        for (int attempt = 0; ; attempt++) {
            try {
                return callOnce(dataSource, action);
            } catch (RuntimeException e) {
                rethrowUnlessRetryableDeadlock(e, attempt);
            }
        }
    }

    private static void runOnce(DataSource dataSource, Consumer<Connection> action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            runAction(connection, action);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi transaction JDBC", e);
        }
    }

    private static <T> T callOnce(DataSource dataSource, Function<Connection, T> action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            return callAction(connection, action);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi transaction JDBC", e);
        }
    }

    private static void rethrowUnlessRetryableDeadlock(RuntimeException failure, int attempt) {
        if (attempt >= MAX_DEADLOCK_RETRIES || !isDeadlock(failure)) {
            throw failure;
        }
        LOG.warn("Transaction bị huỷ vì deadlock — chạy lại lần {}/{}", attempt + 1, MAX_DEADLOCK_RETRIES);
        long backoffMillis = (attempt + 1L)
                * ThreadLocalRandom.current().nextLong(DEADLOCK_BACKOFF_MIN_MILLIS, DEADLOCK_BACKOFF_MAX_MILLIS + 1);
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure;
        }
    }

    /** Có {@link SQLException} mang SQLSTATE deadlock ở bất kỳ tầng nào của chuỗi nguyên nhân. */
    static boolean isDeadlock(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && DEADLOCK_SQL_STATE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
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
