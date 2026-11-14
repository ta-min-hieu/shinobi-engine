package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/** Lô 56 trên Postgres thật: lời nhắn kèm lời mời kết bạn (ADR-376), thống kê PvP (ADR-378), V181. */
@Testcontainers
final class Lot56IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot56tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void requestNoteUpsertsPerPair() {
        long a = createFreshCharacter("RnA");
        long b = createFreshCharacter("RnB");
        dev.kage.server.database.friend.PostgresFriendRequestNoteAdapter adapter =
                new dev.kage.server.database.friend.PostgresFriendRequestNoteAdapter(dataSource);
        assertThat(adapter.find(a, b)).isEmpty();
        adapter.save(a, b, "kết bạn nhé");
        adapter.save(a, b, "đạo hữu ơi");
        assertThat(adapter.find(a, b)).contains("đạo hữu ơi");
        assertThat(adapter.find(b, a)).isEmpty();
    }

    @Test
    void pvpStatsCountBothSides() {
        long killer = createFreshCharacter("PvKiller");
        long victim = createFreshCharacter("PvVictim");
        PostgresPvpStatsAdapter adapter = new PostgresPvpStatsAdapter(dataSource);
        assertThat(adapter.stats(killer)).isEqualTo(new dev.kage.character.domain.PvpStatsPort.Stats(0, 0));
        adapter.recordKill(killer, victim);
        adapter.recordKill(killer, victim);
        assertThat(adapter.stats(killer)).isEqualTo(new dev.kage.character.domain.PvpStatsPort.Stats(2, 0));
        assertThat(adapter.stats(victim)).isEqualTo(new dev.kage.character.domain.PvpStatsPort.Stats(0, 2));
    }

    private static void executeText(String sql) {
        try (java.sql.Connection connection = dataSource.getConnection();
                java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
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
}
