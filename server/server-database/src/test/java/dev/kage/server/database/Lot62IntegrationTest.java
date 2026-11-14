package dev.kage.server.database;

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
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** Lô 62 trên Postgres thật: lịch sử tên bang (ADR-423), câu chào (ADR-425), báo cừu nhân (ADR-426), V184. */
@Testcontainers
final class Lot62IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot62tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void recordsGuildRenamesAndFindsByOldName() {
        long leader = createFreshCharacter("GnLeader");
        executeText(
                "INSERT INTO guild (name, leader_character_id, created_at) VALUES ('GnMoi', " + leader + ", now())");
        long guildId = JdbcOperations.queryOne(
                        dataSource, "SELECT id FROM guild WHERE name = 'GnMoi'", rs -> rs.getLong(1))
                .orElseThrow();
        dev.kage.server.database.guild.PostgresGuildNameHistoryAdapter adapter =
                new dev.kage.server.database.guild.PostgresGuildNameHistoryAdapter(dataSource);
        adapter.record(guildId, "GnCu", "GnMoi", java.time.Instant.parse("2032-01-01T00:00:00Z"));
        assertThat(adapter.history(guildId))
                .extracting(dev.kage.guild.domain.GuildNameHistoryPort.Rename::oldName)
                .containsExactly("GnCu");
        assertThat(adapter.findGuildId("gncu")).contains(guildId);
        assertThat(adapter.findGuildId("GnMoi")).contains(guildId);
        assertThat(adapter.findGuildId("KhongCo")).isEmpty();
    }

    @Test
    void storesAndClearsGreeting() {
        long id = createFreshCharacter("GrA");
        dev.kage.server.database.friend.PostgresLoginGreetingAdapter adapter =
                new dev.kage.server.database.friend.PostgresLoginGreetingAdapter(dataSource);
        adapter.setGreeting(id, "chào chư vị");
        adapter.setGreeting(id, "đạo hữu khoẻ");
        assertThat(adapter.greeting(id)).contains("đạo hữu khoẻ");
        adapter.setGreeting(id, "");
        assertThat(adapter.greeting(id)).isEmpty();
    }

    @Test
    void enemyWatchersRespectMute() {
        long killer = createFreshCharacter("EaKiller");
        long victim = createFreshCharacter("EaVictim");
        executeText("INSERT INTO pvp_enemy (character_id, enemy_character_id, kill_count, last_killed_at) VALUES ("
                + victim + ", " + killer + ", 1, now())");
        dev.kage.server.database.pvp.PostgresEnemyAlertAdapter adapter =
                new dev.kage.server.database.pvp.PostgresEnemyAlertAdapter(dataSource);
        assertThat(adapter.watchersOf(killer)).containsExactly(victim);
        adapter.setMuted(victim, true);
        assertThat(adapter.muted(victim)).isTrue();
        assertThat(adapter.watchersOf(killer)).isEmpty();
        adapter.setMuted(victim, false);
        assertThat(adapter.watchersOf(killer)).containsExactly(victim);
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
