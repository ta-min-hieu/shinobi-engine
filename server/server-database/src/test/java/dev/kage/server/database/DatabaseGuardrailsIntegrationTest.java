package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Test bằng PostgreSQL THẬT — hai hàng rào hạ tầng thêm 2026-10-11 (docs/README.md dòng 45(iii)/(iv)):
 * trần chờ khoá của mọi kết nối trong pool, và việc dọn khoá idempotency cũ.
 */
@Testcontainers
@Timeout(60)
class DatabaseGuardrailsIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    /** SQLSTATE của Postgres cho {@code lock_not_available} — thứ {@code lock_timeout} ném ra. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

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

    /** Kết nối của pool phải MANG hai trần đó — đọc lại từ chính Postgres, không tin cấu hình phía Java. */
    @Test
    void pooledConnectionsShouldCarryBothTimeouts() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(show(connection, "lock_timeout")).isEqualTo("5s");
            assertThat(show(connection, "statement_timeout")).isEqualTo("15s");
        }
    }

    /**
     * Một giao dịch giữ khoá mà không nhả: lệnh thứ hai chạm cùng dòng phải BỎ CUỘC sau trần chờ, chứ
     * không xếp hàng vô hạn. Trước 2026-10-11 nó chờ mãi và giữ luôn một kết nối của pool.
     */
    @Test
    void secondWriterShouldGiveUp_whenTheRowStaysLockedPastTheLockTimeout() throws SQLException {
        UUID key = UUID.randomUUID();
        insertKey(key, Instant.now());
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock =
                    holder.prepareStatement("SELECT key FROM idempotency_key WHERE key = ? FOR UPDATE")) {
                lock.setObject(1, key);
                lock.executeQuery().close();
            }
            long started = System.nanoTime();

            assertThatThrownBy(() -> deleteKey(key))
                    .isInstanceOfSatisfying(
                            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(LOCK_NOT_AVAILABLE));

            assertThat(Duration.ofNanos(System.nanoTime() - started))
                    .as("bỏ cuộc quanh trần chờ khoá, không sớm hơn hẳn và không treo")
                    .isBetween(
                            PostgresDataSourceFactory.LOCK_TIMEOUT.minusSeconds(1),
                            PostgresDataSourceFactory.LOCK_TIMEOUT.plusSeconds(5));
            holder.rollback();
        }
    }

    @Test
    void janitorShouldDeleteOnlyKeysOlderThanTheRetention() {
        Instant now = Instant.now();
        UUID stale = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        insertKey(stale, now.minus(RetentionJanitor.IDEMPOTENCY_KEY_RETENTION).minusSeconds(60));
        insertKey(fresh, now.minus(RetentionJanitor.IDEMPOTENCY_KEY_RETENTION).plusSeconds(60));

        int deleted = new RetentionJanitor(dataSource).purgeIdempotencyKeys(now);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(exists(stale)).isFalse();
        assertThat(exists(fresh))
                .as("khoá còn trong hạn giữ không được đụng tới")
                .isTrue();
    }

    /**
     * Dòng 71(e): audit chat chở nội dung tin — chỉ giữ {@link RetentionJanitor#CHAT_AUDIT_RETENTION}.
     * Dòng audit loại khác (giao dịch, cường hoá…) cũ bao nhiêu cũng không được đụng tới.
     */
    @Test
    void janitorShouldDeleteOnlyChatAuditOlderThanItsRetention() {
        Instant now = Instant.now();
        long characterId = createCharacter("RetentionChat");
        Instant expired = now.minus(RetentionJanitor.CHAT_AUDIT_RETENTION).minusSeconds(60);
        long staleChat = insertAudit(characterId, "CHAT_MESSAGE", expired);
        long freshChat = insertAudit(
                characterId,
                "CHAT_MESSAGE",
                now.minus(RetentionJanitor.CHAT_AUDIT_RETENTION).plusSeconds(60));
        long oldTrade = insertAudit(characterId, "TRADE_EXECUTED", expired);

        int deleted = new RetentionJanitor(dataSource).purgeChatAudit(now);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(auditExists(staleChat)).isFalse();
        assertThat(auditExists(freshChat)).as("tin còn trong hạn giữ").isTrue();
        assertThat(auditExists(oldTrade)).as("audit loại khác không có hạn giữ").isTrue();
    }

    private static long createCharacter(String name) {
        Account[] account = new Account[1];
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> account[0] = new PostgresAccountAdapter().findOrCreate(tx, "retention"));
        Character[] holder = new Character[1];
        NewCharacter newCharacter = new NewCharacter(
                account[0].id(), name, Faction.HOA, UUID.randomUUID().toString());
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = new PostgresCharacterAdapter(dataSource).create(tx, newCharacter));
        return holder[0].id();
    }

    private static long insertAudit(long characterId, String action, Instant createdAt) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, '{}', ?)"
                                + " RETURNING id")) {
            statement.setLong(1, characterId);
            statement.setString(2, action);
            statement.setTimestamp(3, java.sql.Timestamp.from(createdAt));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only chèn audit", e);
        }
    }

    private static boolean auditExists(long id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM audit_log WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc audit", e);
        }
    }

    private static String show(Connection connection, String setting) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT current_setting(?)")) {
            statement.setString(1, setting);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private static void insertKey(UUID key, Instant createdAt) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("INSERT INTO idempotency_key (key, created_at) VALUES (?, ?)")) {
            statement.setObject(1, key);
            statement.setTimestamp(2, java.sql.Timestamp.from(createdAt));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only chèn khoá", e);
        }
    }

    private static void deleteKey(UUID key) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM idempotency_key WHERE key = ?")) {
            statement.setObject(1, key);
            statement.executeUpdate();
        }
    }

    private static boolean exists(UUID key) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT 1 FROM idempotency_key WHERE key = ?")) {
            statement.setObject(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc khoá", e);
        }
    }
}
