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
final class Lot54IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot54tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void likersNewestDayFirst() {
        long target = createFreshCharacter("LkTarget");
        long a = createFreshCharacter("LkA");
        long b = createFreshCharacter("LkB");
        executeText("INSERT INTO profile_like VALUES (" + a + ", " + target + ", '2031-01-01'), (" + b + ", " + target
                + ", '2031-01-05'), (" + a + ", " + target + ", '2031-01-02')");
        PostgresProfileVisitAdapter adapter = new PostgresProfileVisitAdapter(dataSource);
        assertThat(adapter.recentLikers(target, 10))
                .extracting(dev.kage.character.domain.ProfileVisitPort.Liker::name)
                .containsExactly("LkB", "LkA");
    }

    @Test
    void inspectLogKeepsNewestAndFriendsOnlyToggles() {
        long target = createFreshCharacter("IlTarget");
        long viewer = createFreshCharacter("IlViewer");
        long other = createFreshCharacter("IlOther");
        PostgresProfileVisitAdapter adapter = new PostgresProfileVisitAdapter(dataSource);
        java.time.Instant now = java.time.Instant.parse("2031-01-01T00:00:00Z");
        adapter.recordInspect(target, viewer, now, 2);
        adapter.recordInspect(target, other, now.plusSeconds(1), 2);
        adapter.recordInspect(target, viewer, now.plusSeconds(2), 2);
        assertThat(adapter.inspectHistory(target, 10))
                .extracting(dev.kage.character.domain.ProfileVisitPort.Visit::viewerName)
                .containsExactly("IlViewer", "IlOther");
        assertThat(adapter.friendsOnly(target)).isFalse();
        adapter.setFriendsOnly(target, true);
        adapter.setFriendsOnly(target, true);
        assertThat(adapter.friendsOnly(target)).isTrue();
        adapter.setFriendsOnly(target, false);
        assertThat(adapter.friendsOnly(target)).isFalse();
    }

    @Test
    void markAllReadCountsOnlyUnread() {
        long owner = createFreshCharacter("MrOwner");
        executeText("INSERT INTO mail (sender_id, recipient_id, subject, body, gold_amount, created_at) VALUES (NULL, "
                + owner + ", 'a', 'b', 0, now()), (NULL, " + owner + ", 'c', 'd', 0, now())");
        dev.kage.server.database.mail.PostgresMailReadAdapter adapter =
                new dev.kage.server.database.mail.PostgresMailReadAdapter(dataSource);
        assertThat(adapter.markAllRead(owner)).isEqualTo(2);
        assertThat(adapter.markAllRead(owner)).isZero();
        assertThat(adapter.unreadIds(owner)).isEmpty();
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
