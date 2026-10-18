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
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.StorageUseCase;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.StoragePolicy;
import dev.kage.inventory.domain.StoredItem;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Kho đồ tông môn (ADR-061) trên PostgreSQL THẬT: chuyển túi ↔ kho giữ cấp cường hoá, gộp chồng như túi,
 * từ chối món đang mặc / kho đầy / túi đầy / dòng của người khác — và nhánh từ chối không đổi gì.
 */
@Testcontainers
class SectStorageIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int ANY_LEVEL = 100;
    private static final int BAG_SLOTS = 20;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static StorageUseCase storage;

    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác (SpotBugs AT).
    static volatile long accountId;
    static volatile long swordTemplateId;
    static volatile long kunaiTemplateId;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "storagetester"));
        accountId = accountHolder[0].id();
        swordTemplateId = queryLong("SELECT id FROM item_template WHERE code = ?", "wooden_sword");
        kunaiTemplateId = queryLong("SELECT id FROM item_template WHERE code = ?", "kunai");
        storage = new StorageUseCase(
                new StorageUseCase.Ports(
                        new PostgresInventoryAdapter(dataSource),
                        new PostgresEquipmentAdapter(dataSource),
                        new PostgresItemTemplateAdapter(dataSource),
                        new PostgresInventoryTradeOfferAdapter()),
                new PostgresStorageAdapter(dataSource),
                new PostgresAuditAdapter(),
                new JdbcInventoryTransactionRunner(dataSource));
    }

    @Test
    void depositThenWithdraw_shouldKeepTheEnhanceLevel_andBeAudited() {
        long characterId = createFreshCharacter("StoreA");
        long sword = insertItem(characterId, swordTemplateId, 1, 3);

        StorageUseCase.Result deposited = storage.deposit(characterId, sword, 1);

        assertThat(deposited.outcome()).isEqualTo(StorageUseCase.Outcome.OK);
        assertThat(deposited.storage()).singleElement().satisfies(row -> {
            assertThat(row.itemTemplateId()).isEqualTo(swordTemplateId);
            assertThat(row.enhanceLevel()).isEqualTo(3);
        });
        assertThat(queryLong("SELECT count(*) FROM inventory_item WHERE character_id = ?", characterId))
                .isZero();

        StoredItem row = deposited.storage().get(0);
        StorageUseCase.Result withdrawn = storage.withdraw(characterId, row.id(), 1);

        assertThat(withdrawn.outcome()).isEqualTo(StorageUseCase.Outcome.OK);
        assertThat(withdrawn.storage()).isEmpty();
        assertThat(queryLong(
                        "SELECT count(*) FROM inventory_item WHERE character_id = ? AND enhance_level = 3",
                        characterId))
                .isEqualTo(1L);
        assertThat(queryLong(
                        "SELECT count(*) FROM audit_log WHERE character_id = ? AND action LIKE 'STORAGE_%'",
                        characterId))
                .isEqualTo(2L);
    }

    @Test
    void stackablesShouldMergeIntoOneStorageRow() {
        long characterId = createFreshCharacter("StoreB");
        long kunai = insertItem(characterId, kunaiTemplateId, 5, 0);

        storage.deposit(characterId, kunai, 2);
        StorageUseCase.Result second = storage.deposit(characterId, kunai, 3);

        assertThat(second.storage())
                .singleElement()
                .extracting(StoredItem::quantity)
                .isEqualTo(5);
        assertThat(queryLong("SELECT count(*) FROM inventory_item WHERE character_id = ?", characterId))
                .as("chồng 5 phi tiêu đã vào kho hết")
                .isZero();
    }

    @Test
    void shouldRefuseWornItems_otherPeoplesRows_andFullStorage_withoutChangingAnything() {
        long characterId = createFreshCharacter("StoreC");
        long other = createFreshCharacter("StoreD");
        long worn = insertItem(characterId, swordTemplateId, 1, 0);
        new EquipItemUseCase(
                        new PostgresInventoryAdapter(dataSource),
                        new PostgresItemTemplateAdapter(dataSource),
                        new PostgresEquipmentAdapter(dataSource),
                        new JdbcInventoryTransactionRunner(dataSource))
                .execute(characterId, worn, EquipmentSlotType.WEAPON, ANY_LEVEL);
        long foreign = insertItem(other, swordTemplateId, 1, 0);

        assertThat(storage.deposit(characterId, worn, 1).outcome()).isEqualTo(StorageUseCase.Outcome.EQUIPPED);
        assertThat(storage.deposit(characterId, foreign, 1).outcome()).isEqualTo(StorageUseCase.Outcome.NOT_FOUND);

        for (int i = 0; i < StoragePolicy.CAPACITY; i++) {
            write("""
                    INSERT INTO storage_item (character_id, item_template_id, quantity, enhance_level, stored_at)
                    VALUES (?, ?, 1, 0, now())
                    """, characterId, swordTemplateId);
        }
        long spare = insertItem(characterId, swordTemplateId, 1, 0);

        assertThat(storage.deposit(characterId, spare, 1).outcome()).isEqualTo(StorageUseCase.Outcome.STORAGE_FULL);
        assertThat(queryLong("SELECT count(*) FROM inventory_item WHERE id = ?", spare))
                .isEqualTo(1L);
    }

    @Test
    void withdrawIntoAFullBag_shouldBeRefused_andLeaveTheRowInStorage() {
        long characterId = createFreshCharacter("StoreE");
        long sword = insertItem(characterId, swordTemplateId, 1, 0);
        StoredItem row = storage.deposit(characterId, sword, 1).storage().get(0);
        for (int i = 0; i < BAG_SLOTS; i++) {
            insertItem(characterId, swordTemplateId, 1, 0);
        }

        StorageUseCase.Result result = storage.withdraw(characterId, row.id(), 1);

        assertThat(result.outcome()).isEqualTo(StorageUseCase.Outcome.BAG_FULL);
        assertThat(result.storage()).containsExactly(row);
    }

    private static void write(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only ghi: " + sql, e);
        }
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
