package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
 * Test bằng PostgreSQL THẬT — bốn hàng rào CHECK của V52 (2026-10-11).
 *
 * <p>Trước V52 bốn con số này chỉ được CODE giữ, và mỗi lần code để lọt là một nhân vật hỏng: exp âm
 * làm {@code Character} không dựng được (nhân vật không vào world, tài khoản không liệt kê nổi nhân
 * vật), số lượng ≤ 0 làm {@code ItemStack} không dựng được (túi đồ không đọc lên được). Test này ghi
 * THẲNG bằng SQL, bỏ qua mọi lớp gác của ứng dụng — đúng tình huống hàng rào DB sinh ra để chặn: một
 * đường code mới quên kiểm.
 *
 * <p>Mỗi hàng rào kiểm hai phía: giá trị xấu bị từ chối ĐÚNG bởi ràng buộc đó (không phải một lỗi
 * khác tình cờ cùng ném), và giá trị sát biên hợp lệ vẫn ghi được.
 */
@Testcontainers
class NonNegativeColumnChecksIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    /** SQLSTATE của Postgres cho {@code check_violation}. */
    private static final String CHECK_VIOLATION = "23514";

    private static final String KUNAI = "kunai";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác (SpotBugs AT).
    static volatile long characterId;
    static volatile long otherCharacterId;

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

        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "checktester"));
        characterId = createCharacter(accountHolder[0].id(), "CheckOne");
        otherCharacterId = createCharacter(accountHolder[0].id(), "CheckTwo");
    }

    @Test
    void shouldRefuseNegativeExp() {
        assertRefusedBy(
                "character_exp_non_negative",
                "UPDATE character SET exp = exp - 1 WHERE id = ? AND exp = 0",
                characterId);

        assertThat(queryLong("SELECT exp FROM character WHERE id = ?", characterId))
                .isZero();
    }

    @Test
    void shouldRefuseNegativeGold() {
        update("UPDATE character_stat SET gold = 0 WHERE character_id = ?", characterId);

        assertRefusedBy(
                "character_stat_gold_non_negative",
                "UPDATE character_stat SET gold = gold - 1 WHERE character_id = ?",
                characterId);

        assertThat(queryLong("SELECT gold FROM character_stat WHERE character_id = ?", characterId))
                .isZero();
    }

    /** Đúng dòng {@code quantity = -2} mà {@code ctx.give_item(code, -5)} từng để lại (2026-10-10). */
    @Test
    void shouldRefuseAStackGoingBelowOne() {
        long itemId = insertStack(3);

        assertRefusedBy(
                "inventory_item_quantity_positive",
                "UPDATE inventory_item SET quantity = quantity - 5 WHERE id = ?",
                itemId);
        assertRefusedBy(
                "inventory_item_quantity_positive",
                "UPDATE inventory_item SET quantity = quantity - 3 WHERE id = ?",
                itemId);

        update("UPDATE inventory_item SET quantity = quantity - 2 WHERE id = ?", itemId);
        assertThat(queryLong("SELECT quantity FROM inventory_item WHERE id = ?", itemId))
                .as("còn đúng 1 là hợp lệ — hết hẳn thì phải XOÁ dòng chứ không ghi 0")
                .isEqualTo(1);
    }

    @Test
    void shouldRefuseInsertingAnEmptyStack() {
        assertRefusedBy("inventory_item_quantity_positive", """
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                SELECT ?, id, 0, 0, 0 FROM item_template WHERE code = 'kunai'
                """, characterId);
    }

    @Test
    void shouldRefuseNegativeGoldOnEitherSideOfATrade() {
        long sessionId = queryLong("""
                INSERT INTO trade_session (requester_character_id, target_character_id, status, created_at)
                VALUES (?, ?, 'OPEN', now())
                RETURNING id
                """, characterId, otherCharacterId);

        assertRefusedBy(
                "trade_session_gold_non_negative",
                "UPDATE trade_session SET requester_gold = -1 WHERE id = ?",
                sessionId);
        assertRefusedBy(
                "trade_session_gold_non_negative", "UPDATE trade_session SET target_gold = -1 WHERE id = ?", sessionId);

        update("UPDATE trade_session SET requester_gold = 0, target_gold = 5 WHERE id = ?", sessionId);
        assertThat(queryLong("SELECT target_gold FROM trade_session WHERE id = ?", sessionId))
                .isEqualTo(5);
    }

    private static void assertRefusedBy(String constraintName, String sql, long param) {
        assertThatThrownBy(() -> executeRaw(sql, param))
                .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState())
                        .as("phải là check_violation, không phải một lỗi khác")
                        .isEqualTo(CHECK_VIOLATION))
                .hasMessageContaining(constraintName);
    }

    private static long insertStack(int quantity) {
        return queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                SELECT ?, id, ?, 0, 0 FROM item_template WHERE code = ?
                RETURNING id
                """, characterId, quantity, KUNAI);
    }

    private static long createCharacter(long accountId, String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter =
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    private static void update(String sql, long param) {
        try {
            executeRaw(sql, param);
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only chạy SQL: " + sql, e);
        }
    }

    private static void executeRaw(String sql, long param) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, param);
            statement.executeUpdate();
        }
    }

    private static long queryLong(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).as("không có dòng nào: %s", sql).isTrue();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only chạy SQL: " + sql, e);
        }
    }
}
