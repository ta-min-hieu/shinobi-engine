package dev.kage.server.database.trade;

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
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.trade.domain.TradeHistoryPort;

/**
 * ADR-189 — lịch sử giao dịch đọc từ audit TRADE_EXECUTED trên Postgres thật: đúng chiều đưa/nhận, mới nhất trước,
 * trong cửa sổ.
 */
@Testcontainers
final class TradeHistoryIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "tradehistorytester"));
        accountId = accountHolder[0].id();
    }

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    void readsBothSidesFromTheAudit_newestFirst() {
        long me = createFreshCharacter("ThMe");
        long other = createFreshCharacter("ThOther");
        audit(me, NOW.minusSeconds(60), """
                {"sessionId":1,"counterpartId":%d,"goldGiven":500,"goldReceived":0,
                 "itemsGiven":[],"itemsReceived":[{"itemId":9,"quantity":2,"itemTemplateId":20,"enhanceLevel":0}]}
                """.replace("%d", Long.toString(other)));
        audit(me, NOW.minusSeconds(10), """
                {"sessionId":2,"counterpartId":%d,"goldGiven":0,"goldReceived":50,
                 "itemsGiven":[{"itemId":7,"quantity":1,"itemTemplateId":10,"enhanceLevel":3}],"itemsReceived":[]}
                """.replace("%d", Long.toString(other)));
        audit(me, NOW.minus(Duration.ofDays(40)), """
                {"sessionId":0,"counterpartId":%d,"goldGiven":1,"goldReceived":1,"itemsGiven":[],"itemsReceived":[]}
                """.replace("%d", Long.toString(other)));

        List<TradeHistoryPort.Entry> history =
                new PostgresTradeHistoryAdapter(dataSource).recent(me, NOW.minus(Duration.ofDays(30)), 30);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).counterpartName()).isEqualTo("ThOther");
        assertThat(history.get(0).given()).containsExactly(new TradeHistoryPort.Item(10, 1, 3));
        assertThat(history.get(0).goldReceived()).isEqualTo(50);
        assertThat(history.get(1).received()).containsExactly(new TradeHistoryPort.Item(20, 2, 0));
        assertThat(history.get(1).goldGiven()).isEqualTo(500);
        assertThat(new PostgresTradeHistoryAdapter(dataSource).recent(other, NOW.minus(Duration.ofDays(30)), 30))
                .isEmpty(); // dòng audit của người kia là dòng RIÊNG — ở đây chỉ ghi cho "me"
    }

    private static void audit(long characterId, Instant at, String json) {
        JdbcOperations.update(
                dataSource,
                "INSERT INTO audit_log (character_id, action, detail_json, created_at)"
                        + " VALUES (?, 'TRADE_EXECUTED', ?, ?)",
                characterId,
                json.replace("\n", "").strip(),
                Timestamp.from(at));
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
