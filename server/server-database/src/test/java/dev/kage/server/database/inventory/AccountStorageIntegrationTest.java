package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.kage.inventory.application.StorageUseCase;
import dev.kage.inventory.domain.StoredItem;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-197 — kho chung tài khoản trên PostgreSQL THẬT: một nhân vật gửi, nhân vật khác cùng tài khoản rút; tài khoản
 * khác không thấy, không rút được; audit ghi {@code ACCOUNT_STORAGE_*}.
 */
@Testcontainers
class AccountStorageIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static StorageUseCase storage;

    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác (SpotBugs AT).
    static volatile long accountId;
    static volatile long swordTemplateId;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "acctstoretester"));
        accountId = accountHolder[0].id();
        swordTemplateId = queryLong("SELECT id FROM item_template WHERE code = ?", "wooden_sword");
        storage = new StorageUseCase(
                new StorageUseCase.Ports(
                        new PostgresInventoryAdapter(dataSource),
                        new PostgresEquipmentAdapter(dataSource),
                        new PostgresItemTemplateAdapter(dataSource),
                        new PostgresInventoryTradeOfferAdapter()),
                new PostgresAccountStorageAdapter(dataSource),
                new PostgresAuditAdapter(),
                new JdbcInventoryTransactionRunner(dataSource));
    }

    @Test
    void depositedByOneCharacter_withdrawnByAnotherOnTheSameAccount_andHiddenFromOtherAccounts() {
        long first = createFreshCharacter("VaultA");
        long second = createFreshCharacter("VaultB");
        long sword = insertItem(first, swordTemplateId, 1, 2);

        StorageUseCase.Result deposited = storage.deposit(first, sword, 1);
        assertThat(deposited.outcome()).isEqualTo(StorageUseCase.Outcome.OK);
        assertThat(storage.list(second))
                .as("nhân vật khác cùng tài khoản thấy cùng kho")
                .hasSize(1);

        long stranger = createCharacterOnOtherAccount("VaultX");
        StoredItem row = storage.list(second).get(0);
        assertThat(storage.list(stranger)).as("tài khoản khác không thấy").isEmpty();
        assertThat(storage.withdraw(stranger, row.id(), 1).outcome()).isEqualTo(StorageUseCase.Outcome.NOT_FOUND);

        StorageUseCase.Result withdrawn = storage.withdraw(second, row.id(), 1);
        assertThat(withdrawn.outcome()).isEqualTo(StorageUseCase.Outcome.OK);
        assertThat(withdrawn.storage()).isEmpty();
        assertThat(queryLong(
                        "SELECT count(*) FROM inventory_item WHERE character_id = ? AND enhance_level = 2", second))
                .isEqualTo(1L);
        assertThat(queryLong(
                        "SELECT count(*) FROM audit_log WHERE character_id IN (?, ?)"
                                + " AND action LIKE 'ACCOUNT_STORAGE_%'",
                        first, second))
                .isEqualTo(2L);
    }

    private static long createCharacterOnOtherAccount(String name) {
        Account[] holder = new Account[1];
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = new PostgresAccountAdapter().findOrCreate(tx, "acctstoreother"));
        Character[] character = new Character[1];
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> character[0] = new PostgresCharacterAdapter(dataSource)
                        .create(
                                tx,
                                new NewCharacter(
                                        holder[0].id(),
                                        name,
                                        Faction.HOA,
                                        UUID.randomUUID().toString())));
        return character[0].id();
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

    private static long insertItem(long characterId, long templateId, int quantity, int enhanceLevel) {
        return queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                VALUES (?, ?, ?, ?, 0) RETURNING id
                """, characterId, templateId, quantity, enhanceLevel);
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
            throw new IllegalStateException("Lỗi test-only truy vấn: " + sql, e);
        }
    }
}
