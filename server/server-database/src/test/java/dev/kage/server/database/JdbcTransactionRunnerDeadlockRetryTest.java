package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

/**
 * Transaction bị Postgres huỷ để phá deadlock (SQLSTATE 40P01) được chạy lại, tối đa
 * {@link JdbcTransactionRunner#MAX_DEADLOCK_RETRIES} lần (chủ dự án chốt 2026-10-14, docs/README.md
 * dòng 45(ii)). Lỗi khác KHÔNG được chạy lại — một ràng buộc vi phạm chạy lại vẫn vi phạm.
 *
 * <p>Connection là proxy JDK tự viết (chỉ trong test), cùng cách
 * {@link JdbcTransactionRunnerCommitFailureTest} — không cần Postgres để dựng deadlock tất định.
 */
class JdbcTransactionRunnerDeadlockRetryTest {

    @Test
    void shouldRunAgain_whenTheTransactionIsChosenAsADeadlockVictim() {
        AtomicInteger attempts = new AtomicInteger();

        String result = JdbcTransactionRunner.call(fakeDataSource(), connection -> {
            if (attempts.incrementAndGet() <= 2) {
                throw deadlock();
            }
            return "committed";
        });

        assertThat(result).isEqualTo("committed");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldGiveUpAndRethrow_afterTheLastRetry() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> JdbcTransactionRunner.run(fakeDataSource(), connection -> {
                    attempts.incrementAndGet();
                    throw deadlock();
                }))
                .isInstanceOf(DatabaseAccessException.class)
                .satisfies(failure ->
                        assertThat(JdbcTransactionRunner.isDeadlock(failure)).isTrue());
        assertThat(attempts.get()).isEqualTo(1 + JdbcTransactionRunner.MAX_DEADLOCK_RETRIES);
    }

    @Test
    void shouldNotRunAgain_whenTheFailureIsNotADeadlock() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> JdbcTransactionRunner.run(fakeDataSource(), connection -> {
                    attempts.incrementAndGet();
                    throw new DatabaseAccessException("vi phạm unique", new SQLException("dup", "23505"));
                }))
                .isInstanceOf(DatabaseAccessException.class);
        assertThat(attempts.get()).isEqualTo(1);
    }

    private static DatabaseAccessException deadlock() {
        return new DatabaseAccessException(
                "deadlock", new SQLException("deadlock detected", JdbcTransactionRunner.DEADLOCK_SQL_STATE));
    }

    /** Mỗi lần xin một Connection proxy mới: mọi lời gọi (autocommit/commit/rollback/close) là no-op. */
    private static DataSource fakeDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class},
                (proxy, method, args) -> "getConnection".equals(method.getName()) ? noOpConnection() : null);
    }

    private static Connection noOpConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    // GoldLedger dùng Connection làm khoá map ở ranh giới commit/rollback.
                    case "hashCode" -> System.identityHashCode(proxy);
                    // Định danh đúng nghĩa của proxy — equals() ở đây sẽ gọi lại chính handler này.
                    case "equals" -> System.identityHashCode(proxy) == System.identityHashCode(args[0]);
                    default -> method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
    }
}
