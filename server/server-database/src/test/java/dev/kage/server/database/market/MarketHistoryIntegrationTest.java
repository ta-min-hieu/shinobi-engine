package dev.kage.server.database.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
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
import dev.kage.market.domain.MarketHistoryPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** ADR-177 — lịch sử phường thị trên Postgres thật: bán + mua của mình, chỉ dòng SOLD, trong cửa sổ, mới nhất trước. */
@Testcontainers
final class MarketHistoryIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "markethistorytester"));
        accountId = accountHolder[0].id();
    }

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    void soldAndBoughtListingsNewestFirst_onlySoldAndInsideTheWindow() {
        long me = createFreshCharacter("MhMe");
        long other = createFreshCharacter("MhOther");
        long sold = listing(me, other, "SOLD", NOW.minusSeconds(3_600));
        long bought = listing(other, me, "SOLD", NOW.minusSeconds(60));
        listing(me, 0L, "CANCELLED", NOW.minusSeconds(30));
        listing(me, other, "SOLD", NOW.minus(Duration.ofDays(40)));

        List<MarketHistoryPort.Entry> history =
                new PostgresMarketHistoryAdapter(dataSource).recent(me, NOW.minus(Duration.ofDays(30)), 50);

        assertThat(history).extracting(MarketHistoryPort.Entry::listingId).containsExactly(bought, sold);
        assertThat(history.get(0).sold()).isFalse();
        assertThat(history.get(0).counterpartName()).isEqualTo("MhOther");
        assertThat(history.get(1).sold()).isTrue();
        assertThat(history.get(1).price()).isEqualTo(100L);
    }

    private static long listing(long seller, long buyer, String status, Instant closedAt) {
        return JdbcOperations.insertReturningId(
                dataSource,
                """
                INSERT INTO market_listing (seller_id, item_template_id, quantity, price, status, listed_at, expires_at,
                                            buyer_id, closed_at)
                VALUES (?, (SELECT min(id) FROM item_template), 1, 100, ?, ?, ?, ?, ?) RETURNING id
                """,
                seller,
                status,
                Timestamp.from(closedAt.minusSeconds(10)),
                Timestamp.from(closedAt.plus(Duration.ofDays(2))),
                buyer == 0L ? null : buyer,
                Timestamp.from(closedAt));
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
