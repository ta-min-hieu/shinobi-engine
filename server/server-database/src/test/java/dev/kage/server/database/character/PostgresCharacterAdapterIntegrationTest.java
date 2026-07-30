package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.IdempotencyKeyReusedException;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V2 + adapter + idempotency
 * (ADR-025) hoạt động đúng trên schema thật (TESTING.md §2).
 */
@Testcontainers
class PostgresCharacterAdapterIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;

    @BeforeAll
    static void migrateAndCreateAccount() {
        DatabaseConfig config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);

        // character.account_id có FK tới account — cần 1 account thật trước khi test character.
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] holder = new Account[1];
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "charactertester"));
        accountId = holder[0].id();
    }

    @Test
    void shouldCreateCharacter_withStatRow() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId, "KageHero1", Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];

        transactionRunner.inTransaction(tx -> holder[0] = adapter.create(tx, newCharacter));

        assertThat(holder[0].name()).isEqualTo("KageHero1");
        assertThat(holder[0].faction()).isEqualTo(Faction.HOA);
        assertThat(holder[0].level()).isEqualTo(1);
        assertThat(adapter.existsByName("KageHero1")).isTrue();
    }

    @Test
    void shouldRejectSecondCreate_whenIdempotencyKeyReused() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        String idempotencyKey = UUID.randomUUID().toString();
        NewCharacter firstCharacter = new NewCharacter(accountId, "IdemFirst", Faction.HOA, idempotencyKey);
        transactionRunner.inTransaction(tx -> adapter.create(tx, firstCharacter));

        NewCharacter secondCharacter = new NewCharacter(accountId, "IdemSecond", Faction.HOA, idempotencyKey);
        assertThatThrownBy(() -> transactionRunner.inTransaction(tx -> adapter.create(tx, secondCharacter)))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        // Nhân vật thứ 2 KHÔNG được tạo — đúng thiết kế ADR-025 (chống trùng, không phải replay).
        assertThat(adapter.existsByName("IdemSecond")).isFalse();
    }

    @Test
    void shouldListAndCountCharactersOfAccount() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        PostgresAccountAdapter freshAccountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = freshAccountAdapter.findOrCreate(tx, "listtester"));
        long freshAccountId = accountHolder[0].id();

        NewCharacter newCharacter = new NewCharacter(
                freshAccountId, "ListHero", Faction.HOA, UUID.randomUUID().toString());
        transactionRunner.inTransaction(tx -> adapter.create(tx, newCharacter));

        assertThat(adapter.countByAccount(freshAccountId)).isEqualTo(1);
        assertThat(adapter.listByAccount(freshAccountId))
                .extracting(Character::name)
                .containsExactly("ListHero");
    }

    @Test
    void shouldDefaultToOrigin_whenPositionNeverSaved() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId, "PosDefault", Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        transactionRunner.inTransaction(tx -> holder[0] = adapter.create(tx, newCharacter));

        PositionSnapshot position = adapter.loadPosition(holder[0].id());

        assertThat(position.x()).isEqualTo(0f);
        assertThat(position.y()).isEqualTo(0f);
    }

    @Test
    void shouldPersistPosition_acrossSaveAndLoad() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId, "PosSaved", Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        transactionRunner.inTransaction(tx -> holder[0] = adapter.create(tx, newCharacter));

        adapter.savePosition(holder[0].id(), 12.5f, -3.25f);
        PositionSnapshot position = adapter.loadPosition(holder[0].id());

        assertThat(position.x()).isEqualTo(12.5f);
        assertThat(position.y()).isEqualTo(-3.25f);
    }

    @Test
    void shouldLoadStartingStats_afterCreate() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId, "StatHero", Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        transactionRunner.inTransaction(tx -> holder[0] = adapter.create(tx, newCharacter));

        CharacterStats stats = adapter.loadStats(holder[0].id());

        // Số liệu khởi điểm PLACEHOLDER (V2 migration comment + PostgresCharacterAdapter.STARTING_STAT).
        assertThat(stats.str()).isEqualTo(10);
        assertThat(stats.agi()).isEqualTo(10);
        assertThat(stats.intelligence()).isEqualTo(10);
        assertThat(stats.vit()).isEqualTo(10);
    }
}
