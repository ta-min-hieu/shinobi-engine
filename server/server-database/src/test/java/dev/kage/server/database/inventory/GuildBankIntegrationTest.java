package dev.kage.server.database.inventory;

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
 * ADR-198 — kho bang trên PostgreSQL THẬT: thành viên gửi, thành viên khác thấy và rút; mỗi lần ghi một dòng nhật ký
 * bang cùng transaction; người không ở bang nào gửi thì cả transaction lùi lại, món còn nguyên trong túi.
 */
@Testcontainers
class GuildBankIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "guildbanktester"));
        accountId = accountHolder[0].id();
        swordTemplateId = queryLong("SELECT id FROM item_template WHERE code = ?", "wooden_sword");
        storage = new StorageUseCase(
                new StorageUseCase.Ports(
                        new PostgresInventoryAdapter(dataSource),
                        new PostgresEquipmentAdapter(dataSource),
                        new PostgresItemTemplateAdapter(dataSource),
                        new PostgresInventoryTradeOfferAdapter()),
                new PostgresGuildStorageAdapter(dataSource),
                new PostgresAuditAdapter(),
                new JdbcInventoryTransactionRunner(dataSource));
    }

    @Test
    void membersShareTheBank_everyMoveIsLogged_andOutsidersLoseNothing() {
        long leader = createFreshCharacter("BankLead");
        long member = createFreshCharacter("BankMem");
        long outsider = createFreshCharacter("BankOut");
        long guild = queryLong(
                "INSERT INTO guild (name, leader_character_id, created_at)"
                        + " VALUES ('NganKho', ?, now()) RETURNING id",
                leader);
        joinGuild(guild, leader, "LEADER");
        joinGuild(guild, member, "MEMBER");
        long sword = insertItem(member, swordTemplateId, 1, 1);

        assertThat(storage.deposit(member, sword, 1).outcome()).isEqualTo(StorageUseCase.Outcome.OK);
        assertThat(storage.list(leader)).as("cả bang thấy cùng kho").hasSize(1);
        assertThat(storage.list(outsider)).as("người ngoài bang không thấy").isEmpty();
        StoredItem row = storage.list(leader).get(0);
        assertThat(storage.withdraw(leader, row.id(), 1).outcome()).isEqualTo(StorageUseCase.Outcome.OK);
        assertThat(queryLong(
                        "SELECT count(*) FROM guild_log WHERE guild_id = ?"
                                + " AND kind IN ('BANK_DEPOSIT', 'BANK_WITHDRAW')"
                                + " AND detail = 'wooden_sword' AND amount = 1",
                        guild))
                .isEqualTo(2L);

        long outsiderSword = insertItem(outsider, swordTemplateId, 1, 0);
        assertThatThrownBy(() -> storage.deposit(outsider, outsiderSword, 1))
                .as("không ở bang nào — lùi cả transaction")
                .isInstanceOf(RuntimeException.class);
        assertThat(queryLong("SELECT count(*) FROM inventory_item WHERE id = ?", outsiderSword))
                .as("món vẫn còn trong túi")
                .isEqualTo(1L);
    }

    private static void joinGuild(long guildId, long characterId, String role) {
        queryLong(
                "INSERT INTO guild_member (guild_id, character_id, role, joined_at)"
                        + " VALUES (?, ?, ?, now()) RETURNING 1",
                guildId,
                characterId,
                role);
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
