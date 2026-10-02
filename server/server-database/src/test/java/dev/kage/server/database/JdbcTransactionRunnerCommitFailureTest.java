package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@code COMMIT} tự nó cũng có thể thất bại (mất kết nối giữa chừng, ràng buộc DEFERRABLE, lỗi
 * tuần tự hoá) — và lúc đó nó ném {@link SQLException} CHECKED, không phải {@link RuntimeException}.
 *
 * <p>Bug 2026-10-02: {@link JdbcTransactionRunner} chỉ dọn dẹp trong nhánh {@code RuntimeException},
 * nên commit thất bại bỏ qua cả hai việc của nhánh đó: không đếm {@code kage.db.transaction.failed},
 * và không vứt thay đổi vàng đang ghi tạm trong {@link GoldLedger}. Thay đổi bị bỏ rơi nằm lại trong
 * map theo {@link Connection} mãi mãi (rò bộ nhớ), và nếu cùng đối tượng {@link Connection} đó được
 * dùng lại cho một transaction sau, nó BÁO một số dư chưa từng tồn tại cho client.
 *
 * <p>Connection ở đây là proxy JDK tự viết (chỉ trong test) — không cần Postgres để làm commit thất
 * bại một cách tất định.
 */
class JdbcTransactionRunnerCommitFailureTest {

    private static final long CHARACTER_ID = 42L;

    @AfterEach
    void restoreStaticHooks() {
        GoldLedger.configureListener(GoldChangeListener.NO_OP);
        JdbcTransactionRunner.configureMetrics(DbMetricsPort.NO_OP);
    }

    @Test
    void shouldCountFailure_andDropStagedGold_whenCommitItselfFails() {
        List<GoldChange> published = new ArrayList<GoldChange>();
        GoldLedger.configureListener(published::add);
        AtomicInteger failedTransactions = new AtomicInteger();
        JdbcTransactionRunner.configureMetrics(new DbMetricsPort() {
            @Override
            public void recordQueryDuration(String operation, long durationNanos) {
                // không đo ở test này
            }

            @Override
            public void recordTransactionFailed() {
                failedTransactions.incrementAndGet();
            }
        });
        AtomicBoolean failNextCommit = new AtomicBoolean(true);
        Connection connection = fakeConnection(failNextCommit, 500L);
        DataSource dataSource = singleConnectionDataSource(connection);

        assertThatThrownBy(() -> JdbcTransactionRunner.run(
                        dataSource, c -> GoldLedger.credit(c, CHARACTER_ID, 500L, "gold.reason.test")))
                .isInstanceOf(DatabaseAccessException.class);

        assertThat(published).as("commit thất bại thì không ai được báo").isEmpty();
        assertThat(failedTransactions.get()).as("kage.db.transaction.failed").isEqualTo(1);

        // Cùng đối tượng Connection, transaction sau KHÔNG đụng vàng và commit thành công.
        JdbcTransactionRunner.run(dataSource, c -> {});

        assertThat(published)
                .as("số dư của transaction đã thất bại không được rò sang transaction sau")
                .isEmpty();
    }

    // ---- Proxy JDBC tối thiểu: đủ cho đúng một câu UPDATE ... RETURNING gold của GoldLedger ----

    private static Connection fakeConnection(AtomicBoolean failNextCommit, long goldAfterUpdate) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "commit" -> {
                if (failNextCommit.getAndSet(false)) {
                    throw new SQLException("connection lost during COMMIT", "08006");
                }
                yield null;
            }
            case "prepareStatement" -> fakeStatement(goldAfterUpdate);
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> isSameProxy(proxy, args[0]);
            case "toString" -> "FakeConnection";
            default -> defaultValue(method.getReturnType());
        };
        return (Connection) Proxy.newProxyInstance(
                JdbcTransactionRunnerCommitFailureTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                handler);
    }

    private static PreparedStatement fakeStatement(long gold) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "executeQuery" -> fakeResultSet(gold);
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> isSameProxy(proxy, args[0]);
            default -> defaultValue(method.getReturnType());
        };
        return (PreparedStatement) Proxy.newProxyInstance(
                JdbcTransactionRunnerCommitFailureTest.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class},
                handler);
    }

    private static ResultSet fakeResultSet(long gold) {
        AtomicBoolean consumed = new AtomicBoolean(false);
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "next" -> !consumed.getAndSet(true);
            case "getLong" -> gold;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> isSameProxy(proxy, args[0]);
            default -> defaultValue(method.getReturnType());
        };
        return (ResultSet) Proxy.newProxyInstance(
                JdbcTransactionRunnerCommitFailureTest.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private static DataSource singleConnectionDataSource(Connection connection) {
        // getConnection() luôn trả CÙNG đối tượng — đúng tình huống "Connection được dùng lại".
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getConnection" -> connection;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> isSameProxy(proxy, args[0]);
            default -> defaultValue(method.getReturnType());
        };
        return (DataSource) Proxy.newProxyInstance(
                JdbcTransactionRunnerCommitFailureTest.class.getClassLoader(),
                new Class<?>[] {DataSource.class},
                handler);
    }

    // Định danh, không phải equals(): gọi equals() trên proxy sẽ quay lại chính handler này.
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private static boolean isSameProxy(Object proxy, Object other) {
        return proxy == other;
    }
}
