package dev.kage.server.database.character;

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
import dev.kage.character.domain.DeathLogPort;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/**
 * Lô 51 trên Postgres thật: lời nhắn trạng thái (ADR-335), rao vặt (ADR-336), lịch sử tử vong (ADR-337), V177.
 */
@Testcontainers
final class Lot51IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot51tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void statusMessagesSetReadAndClear() {
        long a = createFreshCharacter("StA");
        long b = createFreshCharacter("StB");
        dev.kage.server.database.friend.PostgresStatusMessageAdapter adapter =
                new dev.kage.server.database.friend.PostgresStatusMessageAdapter(dataSource);
        adapter.set(a, "Đang luyện đan");
        adapter.set(a, "Bế quan");
        assertThat(adapter.messageOf(a)).contains("Bế quan");
        assertThat(adapter.messagesOf(List.of(a, b))).containsOnlyKeys(a);
        adapter.set(a, "");
        assertThat(adapter.messageOf(a)).isEmpty();
    }

    @Test
    void bulletinOnePostPerCharacterNewestFirst() {
        long a = createFreshCharacter("BuA");
        long b = createFreshCharacter("BuB");
        dev.kage.server.database.chat.PostgresBulletinAdapter adapter =
                new dev.kage.server.database.chat.PostgresBulletinAdapter(dataSource);
        Instant base = Instant.parse("2030-01-01T00:00:00Z");
        adapter.post(a, "Cần mua kunai", base);
        adapter.post(b, "Bán đan", base.plusSeconds(10));
        adapter.post(a, "Cần mua giáp", base.plusSeconds(20));
        List<dev.kage.chat.domain.BulletinPort.Post> posts = adapter.since(base, 20);
        assertThat(posts)
                .extracting(dev.kage.chat.domain.BulletinPort.Post::body)
                .containsExactly("Cần mua giáp", "Bán đan");
        assertThat(adapter.since(base.plusSeconds(15), 20)).hasSize(1);
        assertThat(adapter.postedAt(a)).contains(base.plusSeconds(20));
        assertThat(adapter.remove(a)).isTrue();
        assertThat(adapter.remove(a)).isFalse();
    }

    @Test
    void deathLogNewestFirst() {
        long character = createFreshCharacter("DlHero");
        PostgresDeathLogAdapter adapter = new PostgresDeathLogAdapter(dataSource);
        adapter.record(character, new DeathLogPort.Entry("", "wild_wolf", 120, Instant.now()));
        adapter.record(character, new DeathLogPort.Entry("BaoDo01", "", -40, Instant.now()));
        List<DeathLogPort.Entry> recent = adapter.recent(character, 10);
        assertThat(recent).extracting(DeathLogPort.Entry::killerName).containsExactly("BaoDo01", "");
        assertThat(recent.get(1).monsterCode()).isEqualTo("wild_wolf");
        assertThat(recent.get(0).x()).isEqualTo(-40);
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
