package dev.kage.server.database.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers (không mock JDBC) — verify migration V1 + adapter
 * hoạt động đúng trên schema thật, không chỉ đúng cú pháp SQL (TESTING.md §2).
 */
@Testcontainers
class PostgresAccountAdapterIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

    private static final int POSTGRES_PORT = 5432;

    @BeforeAll
    static void migrate() {
        DatabaseConfig config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        // Dùng chung PostgresDataSourceFactory với production — vá timezone (xem javadoc factory)
        // áp dụng luôn cho test, không viết lại HikariConfig thủ công (dễ quên lại vá sau).
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);
    }

    @Test
    void shouldCreateAccount_whenUsernameNotSeenBefore() {
        PostgresAccountAdapter adapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner transactionManager = new JdbcAccountTransactionRunner(dataSource);
        Account[] holder = new Account[1];

        transactionManager.inTransaction(tx -> holder[0] = adapter.findOrCreate(tx, "newplayer"));

        assertThat(holder[0].username()).isEqualTo("newplayer");
        assertThat(holder[0].id()).isPositive();
    }

    @Test
    void shouldReturnSameAccount_whenUsernameAlreadyExists() {
        PostgresAccountAdapter adapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner transactionManager = new JdbcAccountTransactionRunner(dataSource);
        Account[] firstHolder = new Account[1];
        Account[] secondHolder = new Account[1];

        transactionManager.inTransaction(tx -> firstHolder[0] = adapter.findOrCreate(tx, "repeatplayer"));
        transactionManager.inTransaction(tx -> secondHolder[0] = adapter.findOrCreate(tx, "repeatplayer"));

        assertThat(secondHolder[0].id()).isEqualTo(firstHolder[0].id());
    }

    @Test
    void shouldRollback_whenActionThrows() throws SQLException {
        PostgresAccountAdapter adapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner transactionManager = new JdbcAccountTransactionRunner(dataSource);

        assertThatThrownBy(() -> transactionManager.inTransaction(tx -> {
                    adapter.findOrCreate(tx, "rollbackplayer");
                    throw new IllegalStateException("Buộc rollback để test");
                }))
                .isInstanceOf(IllegalStateException.class);

        // Kiểm tra trực tiếp bằng SQL thô — findOrCreate() sẽ TẠO MỚI nếu chưa tồn tại nên
        // không dùng lại được để verify; phải SELECT COUNT thẳng để chắc rollback thật sự xảy ra.
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT COUNT(*) FROM account WHERE username = ?")) {
            statement.setString(1, "rollbackplayer");
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                assertThat(resultSet.getInt(1))
                        .as("rollback phải xoá row chưa commit")
                        .isZero();
            }
        }
    }
}
