package dev.kage.server.database.market;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.kage.market.domain.MarketWatchPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** ADR-196 — theo dõi phường thị trên Postgres thật: trần số dòng, sửa giá, ai khớp mức giá, bỏ theo dõi. */
@Testcontainers
final class MarketWatchIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "watchtester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void capsUpdatesMatchesAndRemoves() {
        long watcher = createFreshCharacter("WaOne");
        long other = createFreshCharacter("WaTwo");
        PostgresMarketWatchAdapter adapter = new PostgresMarketWatchAdapter(dataSource);
        long fox = template("spirit_fox");
        long turtle = template("spirit_turtle");
        Instant at = Instant.parse("2026-11-05T08:00:00Z");

        assertThat(adapter.upsert(new MarketWatchPort.WatchRequest(watcher, fox, 100, at), 1))
                .isEqualTo(MarketWatchPort.Upsert.OK);
        assertThat(adapter.upsert(new MarketWatchPort.WatchRequest(watcher, turtle, 100, at), 1))
                .as("trần 1 — dòng mới bị từ chối")
                .isEqualTo(MarketWatchPort.Upsert.TOO_MANY);
        assertThat(adapter.upsert(new MarketWatchPort.WatchRequest(watcher, fox, 300, at), 1))
                .as("sửa dòng có sẵn vẫn được")
                .isEqualTo(MarketWatchPort.Upsert.OK);
        assertThat(adapter.upsert(new MarketWatchPort.WatchRequest(other, fox, 50, at), 1))
                .isEqualTo(MarketWatchPort.Upsert.OK);
        assertThat(adapter.list(watcher)).containsExactly(new MarketWatchPort.Watch(fox, 300));
        assertThat(adapter.watchersFor(fox, 200)).containsExactly(watcher);
        assertThat(adapter.watchersFor(fox, 50)).containsExactlyInAnyOrder(watcher, other);
        assertThat(adapter.watchersFor(turtle, 1)).isEmpty();
        assertThat(adapter.upsert(new MarketWatchPort.WatchRequest(watcher, Long.MAX_VALUE, 10, at), 5))
                .as("mẫu không tồn tại — không vỡ khoá ngoại")
                .isEqualTo(MarketWatchPort.Upsert.UNKNOWN_ITEM);
        assertThat(adapter.remove(watcher, fox)).isTrue();
        assertThat(adapter.remove(watcher, fox)).isFalse();
        assertThat(adapter.watchersFor(fox, 50)).containsExactly(other);
    }

    private static long template(String code) {
        return dev.kage.server.database.JdbcOperations.queryOne(
                        dataSource, "SELECT id FROM item_template WHERE code = ?", rs -> rs.getLong(1), code)
                .orElseThrow();
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
