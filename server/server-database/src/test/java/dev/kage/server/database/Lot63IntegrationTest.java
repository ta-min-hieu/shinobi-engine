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

/** Lô 63 trên Postgres thật: chăm chỉ hôm nay (ADR-431), online 7 ngày (ADR-432), xếp hạng thân mật (ADR-434). */
@Testcontainers
final class Lot63IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot63tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void ranksOnlineTimeOfDayAndListsDays() {
        long a = createFreshCharacter("OnA");
        long b = createFreshCharacter("OnB");
        executeText("INSERT INTO online_time (character_id, day, seconds) VALUES (" + a + ", '2033-03-03', 600), (" + b
                + ", '2033-03-03', 1200), (" + a + ", '2033-03-01', 60), (" + b + ", '2033-03-02', 0)");
        dev.kage.server.database.checkin.PostgresOnlineStatsAdapter adapter =
                new dev.kage.server.database.checkin.PostgresOnlineStatsAdapter(dataSource);
        assertThat(adapter.topOfDay(java.time.LocalDate.parse("2033-03-03"), 10))
                .extracting(dev.kage.checkin.domain.OnlineStatsPort.Ranked::name)
                .containsExactly("OnB", "OnA");
        assertThat(adapter.topOfDay(java.time.LocalDate.parse("2033-03-02"), 10))
                .isEmpty();
        assertThat(adapter.daysOf(a, java.time.LocalDate.parse("2033-02-25"), java.time.LocalDate.parse("2033-03-03")))
                .containsEntry(java.time.LocalDate.parse("2033-03-01"), 60)
                .containsEntry(java.time.LocalDate.parse("2033-03-03"), 600)
                .hasSize(2);
    }

    @Test
    void ranksAcceptedFriendshipsByIntimacy() {
        long a = createFreshCharacter("ImA");
        long b = createFreshCharacter("ImB");
        long c = createFreshCharacter("ImC");
        executeText("INSERT INTO friendship (requester_id, addressee_id, status, created_at, intimacy) VALUES (" + a
                + ", " + b + ", 'ACCEPTED', now(), 900000), (" + a + ", " + c + ", 'PENDING', now(), 990000)");
        dev.kage.server.database.friend.PostgresIntimacyRankingAdapter adapter =
                new dev.kage.server.database.friend.PostgresIntimacyRankingAdapter(dataSource);
        assertThat(adapter.top(1))
                .containsExactly(new dev.kage.friend.domain.IntimacyRankingPort.Pair("ImA", "ImB", 900000));
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
