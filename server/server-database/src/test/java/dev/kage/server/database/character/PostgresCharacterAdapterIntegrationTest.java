package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.IdempotencyKeyReusedException;
import dev.kage.character.domain.NewCharacter;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
        var config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);

        // character.account_id có FK tới account — cần 1 account thật trước khi test character.
        var accountAdapter = new PostgresAccountAdapter();
        var accountTx = new JdbcAccountTransactionRunner(dataSource);
        var holder = new Account[1];
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "charactertester"));
        accountId = holder[0].id();
    }

    @Test
    void shouldCreateCharacter_withStatRow() {
        var adapter = new PostgresCharacterAdapter(dataSource);
        var transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        var newCharacter = new NewCharacter(accountId, "KageHero1", Faction.HOA, UUID.randomUUID().toString());
        var holder = new Character[1];

        transactionRunner.inTransaction(tx -> holder[0] = adapter.create(tx, newCharacter));

        assertThat(holder[0].name()).isEqualTo("KageHero1");
        assertThat(holder[0].faction()).isEqualTo(Faction.HOA);
        assertThat(holder[0].level()).isEqualTo(1);
        assertThat(adapter.existsByName("KageHero1")).isTrue();
    }

    @Test
    void shouldRejectSecondCreate_whenIdempotencyKeyReused() {
        var adapter = new PostgresCharacterAdapter(dataSource);
        var transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        var idempotencyKey = UUID.randomUUID().toString();
        var firstCharacter = new NewCharacter(accountId, "IdemFirst", Faction.HOA, idempotencyKey);
        transactionRunner.inTransaction(tx -> adapter.create(tx, firstCharacter));

        var secondCharacter = new NewCharacter(accountId, "IdemSecond", Faction.HOA, idempotencyKey);
        assertThatThrownBy(() -> transactionRunner.inTransaction(tx -> adapter.create(tx, secondCharacter)))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        // Nhân vật thứ 2 KHÔNG được tạo — đúng thiết kế ADR-025 (chống trùng, không phải replay).
        assertThat(adapter.existsByName("IdemSecond")).isFalse();
    }

    @Test
    void shouldListAndCountCharactersOfAccount() {
        var adapter = new PostgresCharacterAdapter(dataSource);
        var transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        var freshAccountAdapter = new PostgresAccountAdapter();
        var accountTx = new JdbcAccountTransactionRunner(dataSource);
        var accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = freshAccountAdapter.findOrCreate(tx, "listtester"));
        long freshAccountId = accountHolder[0].id();

        var newCharacter = new NewCharacter(freshAccountId, "ListHero", Faction.HOA, UUID.randomUUID().toString());
        transactionRunner.inTransaction(tx -> adapter.create(tx, newCharacter));

        assertThat(adapter.countByAccount(freshAccountId)).isEqualTo(1);
        assertThat(adapter.listByAccount(freshAccountId)).extracting(Character::name).containsExactly("ListHero");
    }
}
