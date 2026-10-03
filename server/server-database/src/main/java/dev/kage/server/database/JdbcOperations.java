package dev.kage.server.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import javax.sql.DataSource;

/**
 * Bọc boilerplate JDBC lặp lại ở mọi {@code Postgres*Adapter} (mở connection/statement, bind
 * param, đọc {@link ResultSet}, đóng resource, bọc {@link SQLException} thành
 * {@link DatabaseAccessException}) — KHÔNG dùng reflection hay container DI (ADR-009: dự án không
 * dùng Spring). Chỉ là hàm tiện ích thuần Java, adapter vẫn tự viết SQL và tự map kết quả qua
 * {@link RowMapper}.
 *
 * <p>Overload nhận {@link Connection} dùng khi đang ở trong transaction (xem
 * {@code JdbcTransactionRunner}); overload nhận {@link DataSource} tự mở/đóng connection cho đọc
 * đơn lẻ ngoài transaction (VD {@code findByX}).
 */
public final class JdbcOperations {

    // Mã lỗi Postgres cho unique_violation (SQLState) — dùng bởi updateOrThrow/insertReturningIdOrThrow.
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    // Mã lỗi Postgres cho foreign_key_violation — dùng bởi isForeignKeyViolation.
    private static final String FOREIGN_KEY_VIOLATION_SQLSTATE = "23503";

    // OBSERVABILITY.md §3.3 kage.db.query.duration — xem javadoc DbMetricsPort: cấu hình 1 LẦN lúc
    // khởi động (ServerDatabaseBootstrap), mặc định NO_OP nên mọi test/call site hiện có không đổi
    // hành vi khi chưa gọi configureMetrics.
    private static volatile DbMetricsPort metricsPort = DbMetricsPort.NO_OP;

    private JdbcOperations() {}

    /**
     * Gọi 1 LẦN lúc khởi động ({@code ServerDatabaseBootstrap}) để bật đo {@code
     * kage.db.query.duration} thật (Micrometer, wiring ở server-gateway) — xem javadoc
     * {@link DbMetricsPort} vì sao là field tĩnh thay vì tham số từng call site.
     */
    public static void configureMetrics(DbMetricsPort port) {
        metricsPort = port;
    }

    // Verb đầu câu SQL (SELECT/INSERT/UPDATE/DELETE) — literal viết tay trong Postgres*Adapter
    // (server/CLAUDE.md "PreparedStatement, không nối chuỗi SQL"), KHÔNG phải input người chơi, nên
    // dùng làm tag Timer an toàn (OBSERVABILITY.md §4: hữu hạn và nhỏ).
    private static String queryOperation(String sql) {
        String trimmed = sql.stripLeading();
        int spaceIndex = trimmed.indexOf(' ');
        String firstWord = spaceIndex > 0 ? trimmed.substring(0, spaceIndex) : trimmed;
        return firstWord.toUpperCase(Locale.ROOT);
    }

    public static <T> List<T> query(DataSource dataSource, String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = dataSource.getConnection()) {
            return query(connection, sql, mapper, params);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi truy vấn: " + sql, e);
        }
    }

    public static <T> List<T> query(Connection connection, String sql, RowMapper<T> mapper, Object... params) {
        long startNanos = System.nanoTime();
        try (PreparedStatement statement = prepare(connection, sql, params);
                ResultSet resultSet = statement.executeQuery()) {
            List<T> results = new ArrayList<T>();
            while (resultSet.next()) {
                results.add(mapper.map(resultSet));
            }
            return results;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi truy vấn: " + sql, e);
        } finally {
            metricsPort.recordQueryDuration(queryOperation(sql), System.nanoTime() - startNanos);
        }
    }

    public static <T> Optional<T> queryOne(DataSource dataSource, String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = query(dataSource, sql, mapper, params);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public static <T> Optional<T> queryOne(Connection connection, String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = query(connection, sql, mapper, params);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** {@code UPDATE}/{@code DELETE} đơn ngoài transaction — tự mở/đóng connection. */
    public static int update(DataSource dataSource, String sql, Object... params) {
        try (Connection connection = dataSource.getConnection()) {
            return update(connection, sql, params);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi update: " + sql, e);
        }
    }

    /** {@code UPDATE}/{@code DELETE} đơn — trả số dòng bị ảnh hưởng. */
    public static int update(Connection connection, String sql, Object... params) {
        try {
            return executeUpdate(connection, sql, params);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi update: " + sql, e);
        }
    }

    /**
     * {@code INSERT} đơn — bắt {@code unique_violation} (idempotency-key/duplicate-request insert),
     * ném {@code onUniqueViolation.get()} thay vì {@link DatabaseAccessException} chung. Pattern lặp
     * lại y hệt ở mọi idempotency-key adapter + {@code sendRequest}/{@code sendJoinRequest}/
     * {@code invite} (friend/guild/party) trước khi có method này.
     */
    public static void updateOrThrow(
            Connection connection, String sql, Supplier<RuntimeException> onUniqueViolation, Object... params) {
        try {
            executeUpdate(connection, sql, params);
        } catch (SQLException e) {
            throw translateUniqueViolation(sql, onUniqueViolation, e, "Lỗi update: ");
        }
    }

    private static int executeUpdate(Connection connection, String sql, Object... params) throws SQLException {
        long startNanos = System.nanoTime();
        try (PreparedStatement statement = prepare(connection, sql, params)) {
            return statement.executeUpdate();
        } finally {
            metricsPort.recordQueryDuration(queryOperation(sql), System.nanoTime() - startNanos);
        }
    }

    /** Như {@link #updateOrThrow} nhưng cho {@code INSERT ... RETURNING id}. */
    public static long insertReturningIdOrThrow(
            Connection connection, String sql, Supplier<RuntimeException> onUniqueViolation, Object... params) {
        try {
            return executeInsertReturningId(connection, sql, params);
        } catch (SQLException e) {
            throw translateUniqueViolation(sql, onUniqueViolation, e, "Lỗi insert: ");
        }
    }

    /**
     * {@code true} nếu {@code e} bọc một {@code foreign_key_violation} — INSERT tham chiếu tới một
     * dòng cha mà giao dịch khác vừa xoá (bang/party bị giải tán) giữa lúc use case kiểm tra và lúc
     * ghi. Adapter dịch nó thành exception nghiệp vụ thay vì để thành 500.
     */
    public static boolean isForeignKeyViolation(DatabaseAccessException e) {
        return e.getCause() instanceof SQLException cause && FOREIGN_KEY_VIOLATION_SQLSTATE.equals(cause.getSQLState());
    }

    /** {@code INSERT ... RETURNING id} ngoài transaction — tự mở/đóng connection. */
    public static long insertReturningId(DataSource dataSource, String sql, Object... params) {
        try (Connection connection = dataSource.getConnection()) {
            return insertReturningId(connection, sql, params);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi insert: " + sql, e);
        }
    }

    /** {@code INSERT ... RETURNING id} — trả id vừa sinh. SQL phải có mệnh đề {@code RETURNING}. */
    public static long insertReturningId(Connection connection, String sql, Object... params) {
        try {
            return executeInsertReturningId(connection, sql, params);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi insert: " + sql, e);
        }
    }

    private static long executeInsertReturningId(Connection connection, String sql, Object... params)
            throws SQLException {
        long startNanos = System.nanoTime();
        try (PreparedStatement statement = prepare(connection, sql, params);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        } finally {
            metricsPort.recordQueryDuration(queryOperation(sql), System.nanoTime() - startNanos);
        }
    }

    private static RuntimeException translateUniqueViolation(
            String sql, Supplier<RuntimeException> onUniqueViolation, SQLException e, String messagePrefix) {
        if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
            return onUniqueViolation.get();
        }
        return new DatabaseAccessException(messagePrefix + sql, e);
    }

    /**
     * Chạy {@code SELECT ... FOR UPDATE} để khoá dòng, bỏ qua kết quả trả về — Postgres áp khoá
     * ngay lúc {@code executeQuery()} chạy, không cần lặp {@link ResultSet}. Tránh dùng
     * {@link #query} (trả {@code List}) chỉ để khoá dòng rồi vứt kết quả.
     */
    public static void lock(Connection connection, String sql, Object... params) {
        long startNanos = System.nanoTime();
        try (PreparedStatement statement = prepare(connection, sql, params)) {
            // ResultSet không cần đọc — statement.close() tự đóng nó theo spec JDBC.
            statement.executeQuery();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi khoá dòng: " + sql, e);
        } finally {
            metricsPort.recordQueryDuration(queryOperation(sql), System.nanoTime() - startNanos);
        }
    }

    /** Batch cùng 1 SQL với nhiều bộ tham số (VD xoá hàng loạt theo danh sách id). No-op nếu rỗng. */
    public static void batchUpdate(Connection connection, String sql, List<Object[]> paramsList) {
        if (paramsList.isEmpty()) {
            return;
        }
        long startNanos = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Object[] params : paramsList) {
                bindParams(statement, params);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi batch update: " + sql, e);
        } finally {
            metricsPort.recordQueryDuration(queryOperation(sql), System.nanoTime() - startNanos);
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        bindParams(statement, params);
        return statement;
    }

    // UUID cần Types.OTHER tường minh (pattern lặp lại y hệt ở mọi idempotency-key insert trước
    // khi có class này — VD PostgresTradeIdempotencyAdapter) — không phải suy đoán.
    private static void bindParams(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param instanceof UUID) {
                statement.setObject(i + 1, param, Types.OTHER);
            } else {
                statement.setObject(i + 1, param);
            }
        }
    }
}
