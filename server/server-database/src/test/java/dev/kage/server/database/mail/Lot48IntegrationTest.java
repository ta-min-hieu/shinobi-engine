package dev.kage.server.database.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** Lô 48 trên Postgres thật: thư chưa đọc (ADR-314), quota thư bạn bè (ADR-312), migration V174. */
@Testcontainers
final class Lot48IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot48tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void tracksUnreadMailAndMarksRead() {
        long recipient = createFreshCharacter("UrRecv");
        long sender = createFreshCharacter("UrSender");
        execute(
                "INSERT INTO mail (sender_id, recipient_id, subject, body, gold_amount, created_at) " + "VALUES ("
                        + sender + ", ?, 'a', 'b', 0, now())",
                recipient);
        execute(
                "INSERT INTO mail (sender_id, recipient_id, subject, body, gold_amount, created_at) " + "VALUES ("
                        + sender + ", ?, 'c', 'd', 0, now())",
                recipient);
        PostgresMailReadAdapter adapter = new PostgresMailReadAdapter(dataSource);
        List<Long> ids = List.copyOf(adapter.unreadIds(recipient));
        assertThat(ids).hasSize(2);

        assertThat(adapter.markRead(ids.getFirst(), sender)).isFalse();
        assertThat(adapter.markRead(ids.getFirst(), recipient)).isTrue();
        assertThat(adapter.unreadIds(recipient)).hasSize(1).doesNotContain(ids.getFirst());
    }

    @Test
    void countsFriendMailBatchesSinceDayStart() {
        long sender = createFreshCharacter("FmSender");
        PostgresFriendMailQuotaAdapter adapter = new PostgresFriendMailQuotaAdapter(dataSource);
        Instant now = Instant.now();
        assertThat(adapter.sentSince(sender, now.minusSeconds(60))).isZero();
        adapter.record(sender, 3, now);
        assertThat(adapter.sentSince(sender, now.minusSeconds(60))).isEqualTo(1);
        assertThat(adapter.sentSince(sender, now.plusSeconds(60))).isZero();
    }

    private static void execute(String sql, long id) {
        try (java.sql.Connection connection = dataSource.getConnection();
                java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
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
