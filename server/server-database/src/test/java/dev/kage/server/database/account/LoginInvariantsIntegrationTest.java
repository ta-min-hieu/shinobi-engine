package dev.kage.server.database.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.application.LoginOrCreateAccountUseCase;
import dev.kage.account.application.LoginResult;
import dev.kage.account.domain.SessionPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;

/**
 * Bất biến của đăng nhập trên PostgreSQL THẬT, đo bằng GIÁ TRỊ CUỐI (số dòng {@code account}, số
 * token đã phát) chứ không bằng "có ném lỗi không" — bài học PvP rating race.
 *
 * <ul>
 *   <li>Nhiều kết nối đăng nhập LẦN ĐẦU cùng một username cùng lúc ⇒ đúng một dòng account, mọi
 *       lượt nhận cùng một {@code accountId}, không lượt nào lỗi (upsert {@code ON CONFLICT DO
 *       NOTHING} + đọc lại dưới READ COMMITTED).
 *   <li>Username độc (rỗng, khoảng trắng, ký tự vô hình, RTL, NUL, 10k ký tự...) ⇒ bị từ chối là
 *       đầu vào sai, KHÔNG ghi dòng nào, KHÔNG phát token nào.
 * </ul>
 */
@Testcontainers
class LoginInvariantsIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int CONCURRENT_LOGINS = 16;
    private static final int ROUNDS = 5;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        DatabaseConfig config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);
    }

    @Test
    void shouldCreateExactlyOneAccount_whenSameNewUsernameLogsInConcurrently() throws Exception {
        CountingSessionPort sessions = new CountingSessionPort();
        LoginOrCreateAccountUseCase useCase = new LoginOrCreateAccountUseCase(
                new PostgresAccountAdapter(), sessions, new JdbcAccountTransactionRunner(dataSource));
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_LOGINS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String username = "loginrace" + round;
                CyclicBarrier barrier = new CyclicBarrier(CONCURRENT_LOGINS);
                List<Future<LoginResult>> futures = new ArrayList<>();
                for (int i = 0; i < CONCURRENT_LOGINS; i++) {
                    futures.add(executor.submit(() -> {
                        barrier.await(1, TimeUnit.MINUTES);
                        return useCase.execute(username);
                    }));
                }
                Set<Long> accountIds = new HashSet<>();
                Set<String> tokens = new HashSet<>();
                for (Future<LoginResult> future : futures) {
                    // get() ném nếu bất kỳ lượt nào lỗi — "không lượt nào lỗi" là một phần của bất biến.
                    LoginResult result = future.get(1, TimeUnit.MINUTES);
                    accountIds.add(result.accountId());
                    tokens.add(result.sessionToken());
                    assertThat(result.username()).isEqualTo(username);
                }

                assertThat(countAccounts(username))
                        .as("số dòng account của '%s'", username)
                        .isEqualTo(1);
                assertThat(accountIds)
                        .as("mọi lượt đăng nhập phải nhận cùng một account")
                        .hasSize(1);
                assertThat(tokens).as("mỗi lượt đăng nhập một token riêng").hasSize(CONCURRENT_LOGINS);
                assertThat(sessions.ownersOf(tokens))
                        .containsOnly(accountIds.iterator().next());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectHostileUsernames_withoutWritingAnyRowOrIssuingAnySession() throws SQLException {
        CountingSessionPort sessions = new CountingSessionPort();
        LoginOrCreateAccountUseCase useCase = new LoginOrCreateAccountUseCase(
                new PostgresAccountAdapter(), sessions, new JdbcAccountTransactionRunner(dataSource));
        long accountsBefore = countAllAccounts();

        for (String hostile : hostileUsernames()) {
            assertThatThrownBy(() -> useCase.execute(hostile))
                    .as("username độc phải bị từ chối là đầu vào sai: %s", describe(hostile))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(countAllAccounts()).as("không username độc nào được ghi").isEqualTo(accountsBefore);
        assertThat(sessions.issuedCount()).as("không token nào được phát").isZero();
    }

    static List<String> hostileUsernames() {
        return List.of(
                "",
                " ",
                "   ",
                "\t\t\t",
                "kage hero",
                " kagehero",
                "kagehero ",
                "kagehero\n",
                "kage\u0000hero",
                "\u0000\u0000\u0000",
                "kage\u200Bhero", // zero-width space
                "kage\u200Dhero", // zero-width joiner
                "\u200B\u200B\u200B",
                "kage\u202Eorehc", // right-to-left override
                "\u05E9\u05DC\u05D5\u05DD", // chữ Hebrew (RTL) — username chỉ nhận ASCII
                "k\u0430ge", // 'а' Cyrillic giả 'a' Latin
                "\uFF4B\uFF41\uFF47\uFF45", // 'kage' khổ toàn phần
                "kag\u00E9",
                "kage\uD800", // nửa cặp surrogate lẻ
                "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00", // emoji
                "a".repeat(21),
                "a".repeat(10_000),
                "kage'; DROP TABLE account; --",
                "%s%s%s%n");
    }

    static String describe(String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints().limit(24).forEach(cp -> out.append(String.format("U+%04X ", cp)));
        return out.append("(len=").append(value.length()).append(')').toString();
    }

    private static int countAccounts(String username) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT COUNT(*) FROM account WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static long countAllAccounts() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM account");
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    /** Session in-memory an toàn đa luồng — đếm số token đã phát và chủ của từng token. */
    private static final class CountingSessionPort implements SessionPort {
        private final AtomicLong nextToken = new AtomicLong();
        private final Map<String, Long> owners = new ConcurrentHashMap<>();

        @Override
        public String issue(long accountId) {
            String token = "token-" + nextToken.incrementAndGet();
            owners.put(token, accountId);
            return token;
        }

        @Override
        public Optional<Long> validateAndRenew(String token) {
            return Optional.ofNullable(owners.get(token));
        }

        @Override
        public void revoke(String token) {
            owners.remove(token);
        }

        long issuedCount() {
            return nextToken.get();
        }

        Set<Long> ownersOf(Set<String> tokens) {
            Set<Long> result = new HashSet<>();
            for (String token : tokens) {
                result.add(owners.get(token));
            }
            return result;
        }
    }
}
