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

/** Lô 58 trên Postgres thật: xếp hạng sát thủ / tu luyện (ADR-391/392), thâm niên bang (ADR-393). */
@Testcontainers
final class Lot58IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot58tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void ranksKillsAndPlaytime() {
        long a = createFreshCharacter("RkA");
        long b = createFreshCharacter("RkB");
        executeText("INSERT INTO character_lifetime_stats (character_id, player_kills, playtime_seconds) VALUES (" + a
                + ", 900001, 5), (" + b + ", 900002, 900000007)");
        dev.kage.server.database.character.PostgresLifetimeRankingAdapter adapter =
                new dev.kage.server.database.character.PostgresLifetimeRankingAdapter(dataSource);
        assertThat(adapter.topPlayerKills(2))
                .extracting(dev.kage.character.domain.LifetimeRankingPort.Ranked::name)
                .containsExactly("RkB", "RkA");
        assertThat(adapter.topPlaytime(1))
                .containsExactly(new dev.kage.character.domain.LifetimeRankingPort.Ranked("RkB", 900_000_007L));
    }

    @Test
    void listsSeniorityOldestFirst() {
        long leader = createFreshCharacter("SnLeader");
        long late = createFreshCharacter("SnLate");
        executeText(
                "INSERT INTO guild (name, leader_character_id, created_at) VALUES ('SnGuild', " + leader + ", now())");
        executeText("INSERT INTO guild_member (guild_id, character_id, role, joined_at) SELECT id, " + late
                + ", 'MEMBER', '2032-02-01' FROM guild WHERE name = 'SnGuild'");
        executeText("INSERT INTO guild_member (guild_id, character_id, role, joined_at) SELECT id, " + leader
                + ", 'LEADER', '2032-01-01' FROM guild WHERE name = 'SnGuild'");
        long guildId = dev.kage.server.database.JdbcOperations.queryOne(
                        dataSource, "SELECT id FROM guild WHERE name = 'SnGuild'", rs -> rs.getLong(1))
                .orElseThrow();
        assertThat(new dev.kage.server.database.guild.PostgresGuildDirectoryAdapter(dataSource).seniority(guildId))
                .extracting(dev.kage.guild.domain.GuildDirectoryPort.Senior::name)
                .containsExactly("SnLeader", "SnLate");
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
