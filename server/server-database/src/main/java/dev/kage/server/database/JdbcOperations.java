package dev.kage.server.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
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

    private JdbcOperations() {
    }

    public static <T> List<T> query(DataSource dataSource, String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = dataSource.getConnection()) {
            return query(connection, sql, mapper, params);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi truy vấn: " + sql, e);
        }
    }

    public static <T> List<T> query(Connection connection, String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement statement = prepare(connection, sql, params);
                ResultSet resultSet = statement.executeQuery()) {
            List<T> results = new ArrayList<T>();
            while (resultSet.next()) {
                results.add(mapper.map(resultSet));
            }
            return results;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi truy vấn: " + sql, e);
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
        try (PreparedStatement statement = prepare(connection, sql, params)) {
            return statement.executeUpdate();
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
        try (PreparedStatement statement = prepare(connection, sql, params)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
                throw onUniqueViolation.get();
            }
            throw new DatabaseAccessException("Lỗi update: " + sql, e);
        }
    }

    /** Như {@link #updateOrThrow} nhưng cho {@code INSERT ... RETURNING id}. */
    public static long insertReturningIdOrThrow(
            Connection connection, String sql, Supplier<RuntimeException> onUniqueViolation, Object... params) {
        try (PreparedStatement statement = prepare(connection, sql, params);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
                throw onUniqueViolation.get();
            }
            throw new DatabaseAccessException("Lỗi insert: " + sql, e);
        }
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
        try (PreparedStatement statement = prepare(connection, sql, params);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi insert: " + sql, e);
        }
    }

    /** Batch cùng 1 SQL với nhiều bộ tham số (VD xoá hàng loạt theo danh sách id). No-op nếu rỗng. */
    public static void batchUpdate(Connection connection, String sql, List<Object[]> paramsList) {
        if (paramsList.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Object[] params : paramsList) {
                bindParams(statement, params);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi batch update: " + sql, e);
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
