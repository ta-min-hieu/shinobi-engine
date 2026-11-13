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

/**
 * Lô 53 trên Postgres thật: xếp hạng chuỗi / giới thiệu (ADR-351/352), tìm người (ADR-353), lưu trữ thư (ADR-354),
 * V179.
 */
@Testcontainers
final class Lot53IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot53tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void ranksStreaksAndReferrers() {
        long a = createFreshCharacter("RkA");
        long b = createFreshCharacter("RkB");
        long c = createFreshCharacter("RkC");
        java.time.LocalDate today = java.time.LocalDate.of(2031, 1, 10);
        executeText("INSERT INTO login_streak VALUES (" + a + ", 5, 5, '2031-01-10'), (" + b
                + ", 9, 9, '2031-01-09'), (" + c + ", 20, 20, '2031-01-01')");
        executeText("INSERT INTO referral (referred_id, referrer_id, rewarded_at) VALUES (" + b + ", " + a
                + ", now()), (" + c + ", " + a + ", now())");
        PostgresPlayerLookupAdapter adapter = new PostgresPlayerLookupAdapter(dataSource);
        assertThat(adapter.topStreaks(today.minusDays(1), 10))
                .extracting(dev.kage.character.domain.PlayerLookupPort.Ranked::name)
                .containsSubsequence("RkB", "RkA")
                .doesNotContain("RkC");
        assertThat(adapter.topReferrers(10))
                .filteredOn(ranked -> ranked.name().equals("RkA"))
                .extracting(dev.kage.character.domain.PlayerLookupPort.Ranked::value)
                .containsExactly(2);
    }

    @Test
    void searchMatchesSubstringAndEscapesWildcards() {
        createFreshCharacter("TimKiem_Ab");
        createFreshCharacter("ZzTimkiem");
        PostgresPlayerLookupAdapter adapter = new PostgresPlayerLookupAdapter(dataSource);
        assertThat(adapter.search("timkiem", 10))
                .extracting(dev.kage.character.domain.PlayerLookupPort.Hit::name)
                .containsExactly("TimKiem_Ab", "ZzTimkiem");
        assertThat(adapter.search("m_a", 10))
                .extracting(dev.kage.character.domain.PlayerLookupPort.Hit::name)
                .containsExactly("TimKiem_Ab");
        assertThat(adapter.search("%", 10)).isEmpty();
    }

    @Test
    void archivedMailLeavesTheInbox() {
        long owner = createFreshCharacter("ArOwner");
        long stranger = createFreshCharacter("ArOther");
        executeText("INSERT INTO mail (sender_id, recipient_id, subject, body, gold_amount, created_at) VALUES (NULL, "
                + owner + ", 'Lưu tôi', 'nội dung', 0, now())");
        long mailId = dev.kage.server.database.JdbcOperations.queryOne(
                        dataSource, "SELECT id FROM mail WHERE recipient_id = ?", rs -> rs.getLong(1), owner)
                .orElseThrow();
        dev.kage.server.database.mail.PostgresMailArchiveAdapter adapter =
                new dev.kage.server.database.mail.PostgresMailArchiveAdapter(dataSource);
        assertThat(adapter.setArchived(mailId, stranger, true)).isFalse();
        assertThat(adapter.setArchived(mailId, owner, true)).isTrue();
        assertThat(adapter.archived(owner))
                .extracting(dev.kage.mail.domain.MailArchivePort.Entry::subject)
                .containsExactly("Lưu tôi");
        assertThat(adapter.setArchived(mailId, owner, false)).isTrue();
        assertThat(adapter.archived(owner)).isEmpty();
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
