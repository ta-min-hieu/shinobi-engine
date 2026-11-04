package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.inventory.domain.GemRemovalPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-168 — tháo Linh Ngọc trên Postgres thật: ô khảm biến mất, viên ngọc về túi, trừ phí; thiếu vàng / chưa khảm / món
 * người khác thì từ chối và không đổi gì.
 */
@Testcontainers
final class GemRemovalIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final long FEE = 500L;
    private static final Instant AT = Instant.parse("2026-11-04T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long fire;
    static long sword;
    static PostgresGemRemovalAdapter adapter;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "gemremovaltester"));
        accountId = accountHolder[0].id();
        fire = queryLong("SELECT id FROM item_template WHERE code = ?", "spirit_jade_fire");
        sword = queryLong("SELECT id FROM item_template WHERE code = ?", "wooden_sword");
        adapter = new PostgresGemRemovalAdapter(dataSource);
    }

    @Test
    void unsocketShouldReturnTheGemAndChargeTheFee() {
        long owner = createFreshCharacter("UnsA");
        long item = socketedSword(owner);
        setGold(owner, FEE + 7);

        GemRemovalPort.Removed removed = adapter.unsocket(owner, item, FEE, AT);

        assertThat(removed.outcome()).isEqualTo(GemRemovalPort.Outcome.OK);
        assertThat(removed.gemItemTemplateId()).isEqualTo(fire);
        assertThat(queryLong("SELECT COUNT(*) FROM item_socket WHERE inventory_item_id = ?", item))
                .isZero();
        assertThat(count(owner, fire)).isEqualTo(1L);
        assertThat(queryLong("SELECT gold FROM character_stat WHERE character_id = ?", owner))
                .isEqualTo(7L);
        assertThat(adapter.unsocket(owner, item, FEE, AT).outcome()).isEqualTo(GemRemovalPort.Outcome.NOT_SOCKETED);
    }

    @Test
    void refusalsShouldChangeNothing() {
        long owner = createFreshCharacter("UnsB");
        long stranger = createFreshCharacter("UnsC");
        long item = socketedSword(owner);
        setGold(owner, FEE - 1);
        setGold(stranger, FEE * 2);

        assertThat(adapter.unsocket(owner, item, FEE, AT).outcome()).isEqualTo(GemRemovalPort.Outcome.NOT_ENOUGH_GOLD);
        assertThat(adapter.unsocket(stranger, item, FEE, AT).outcome()).isEqualTo(GemRemovalPort.Outcome.NOT_FOUND);
        assertThat(queryLong("SELECT COUNT(*) FROM item_socket WHERE inventory_item_id = ?", item))
                .isEqualTo(1L);
        assertThat(count(owner, fire)).isZero();
    }

    private static long socketedSword(long characterId) {
        long item = queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                VALUES (?, ?, 1, 0, 0) RETURNING id
                """, characterId, sword);
        queryLong("""
                INSERT INTO item_socket (inventory_item_id, gem_item_template_id, socketed_at)
                VALUES (?, ?, now()) RETURNING inventory_item_id
                """, item, fire);
        return item;
    }

    private static long count(long characterId, long templateId) {
        return queryLong(
                "SELECT COALESCE(SUM(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?",
                characterId,
                templateId);
    }

    private static void setGold(long characterId, long gold) {
        queryLong("UPDATE character_stat SET gold = ? WHERE character_id = ? RETURNING gold", gold, characterId);
    }

    private static long createFreshCharacter(String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter =
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    private static long queryLong(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only truy vấn: " + sql, e);
        }
    }
}
