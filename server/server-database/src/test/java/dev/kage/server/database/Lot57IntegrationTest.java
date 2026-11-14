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

/** Lô 57 trên Postgres thật: chế độ ẩn (ADR-383), thích tuần (ADR-384), dọn thư cũ đã đọc (ADR-386), V182. */
@Testcontainers
final class Lot57IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot57tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void invisibleModeToggles() {
        long id = createFreshCharacter("InvA");
        dev.kage.server.database.friend.PostgresInvisibleModeAdapter adapter =
                new dev.kage.server.database.friend.PostgresInvisibleModeAdapter(dataSource);
        assertThat(adapter.isInvisible(id)).isFalse();
        adapter.setInvisible(id, true);
        adapter.setInvisible(id, true);
        assertThat(adapter.isInvisible(id)).isTrue();
        adapter.setInvisible(id, false);
        assertThat(adapter.isInvisible(id)).isFalse();
    }

    @Test
    void weeklyLikesCountOnlyRecentDays() {
        long old = createFreshCharacter("WkOld");
        long fresh = createFreshCharacter("WkFresh");
        long a = createFreshCharacter("WkA");
        long b = createFreshCharacter("WkB");
        executeText("INSERT INTO profile_like VALUES (" + a + ", " + old + ", '2032-01-01'), (" + b + ", " + old
                + ", '2032-01-02'), (" + a + ", " + fresh + ", '2032-01-09')");
        dev.kage.server.database.character.PostgresProfileLikeAdapter adapter =
                new dev.kage.server.database.character.PostgresProfileLikeAdapter(dataSource);
        assertThat(adapter.topSince(java.time.LocalDate.parse("2032-01-03"), 10))
                .extracting(dev.kage.character.domain.ProfileLikePort.Ranked::name)
                .containsExactly("WkFresh");
    }

    @Test
    void purgesOnlyOldReadMailWithoutPendingGifts() {
        long owner = createFreshCharacter("MpOwner");
        String old = "now() - interval '40 days'";
        executeText("INSERT INTO mail (recipient_id, subject, body, gold_amount, created_at, read_at, pinned) VALUES "
                + "(" + owner + ", 'cu', 'b', 0, " + old + ", " + old + ", false), "
                + "(" + owner + ", 'ghim', 'b', 0, " + old + ", " + old + ", true), "
                + "(" + owner + ", 'vang', 'b', 50, " + old + ", " + old + ", false), "
                + "(" + owner + ", 'moi', 'b', 0, now(), now(), false), "
                + "(" + owner + ", 'chuadoc', 'b', 0, " + old + ", NULL, false)");
        RetentionJanitor janitor = new RetentionJanitor(dataSource);
        assertThat(janitor.purgeOldReadMail(java.time.Instant.now())).isEqualTo(1);
        assertThat(janitor.purgeOldReadMail(java.time.Instant.now())).isZero();
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
