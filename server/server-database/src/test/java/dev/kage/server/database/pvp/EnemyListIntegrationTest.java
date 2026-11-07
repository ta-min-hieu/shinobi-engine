package dev.kage.server.database.pvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import dev.kage.pvp.domain.EnemyListPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** ADR-194 — cừu nhân trên Postgres thật: cộng dồn, mới nhất trước, cắt theo trần, xoá theo tên. */
@Testcontainers
final class EnemyListIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "enemytester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void countsOrdersTrimsAndRemoves() {
        long victim = createFreshCharacter("EnVictim");
        long first = createFreshCharacter("EnFirst");
        long second = createFreshCharacter("EnSecond");
        long third = createFreshCharacter("EnThird");
        PostgresEnemyListAdapter adapter = new PostgresEnemyListAdapter(dataSource);
        Instant at = Instant.parse("2026-11-05T08:00:00Z");

        adapter.recordKill(victim, first, at, 2);
        adapter.recordKill(victim, first, at.plusSeconds(10), 2);
        adapter.recordKill(victim, second, at.plusSeconds(20), 2);
        assertThat(adapter.list(victim))
                .extracting(EnemyListPort.Enemy::name, EnemyListPort.Enemy::killCount)
                .containsExactly(tuple("EnSecond", 1), tuple("EnFirst", 2));
        adapter.recordKill(victim, third, at.plusSeconds(30), 2);
        assertThat(adapter.list(victim))
                .as("trần 2 — người cũ nhất rơi ra")
                .extracting(EnemyListPort.Enemy::name)
                .containsExactly("EnThird", "EnSecond");
        assertThat(adapter.remove(victim, "EnSecond")).isTrue();
        assertThat(adapter.remove(victim, "EnSecond")).isFalse();
        assertThat(adapter.list(victim)).extracting(EnemyListPort.Enemy::name).containsExactly("EnThird");
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
}
