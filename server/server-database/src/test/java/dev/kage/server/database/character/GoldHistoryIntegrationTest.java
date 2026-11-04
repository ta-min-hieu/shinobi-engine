package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.GoldHistoryPort;
import dev.kage.character.domain.NewCharacter;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.GoldChangeListener;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/**
 * ADR-169 — sổ vàng trên Postgres thật: mỗi lần GoldLedger trừ/cộng ghi một dòng (số đổi, số dư, lý do) trong CÙNG
 * transaction; transaction rollback thì dòng sổ cũng không còn; đọc mới nhất trước.
 */
@Testcontainers
final class GoldHistoryIntegrationTest {

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
        GoldLedger.configureListener(GoldChangeListener.NO_OP);
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "goldhistorytester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void everyCommittedChangeShouldBeLoggedNewestFirst() {
        long owner = createFreshCharacter("GoldA");
        setGold(owner, 1000);

        JdbcTransactionRunner.call(dataSource, c -> {
            GoldLedger.credit(c, owner, 250, "gold.reason.sell_item");
            return GoldLedger.deduct(c, owner, 100, "gold.reason.enhance_item");
        });

        List<GoldHistoryPort.Entry> entries = new PostgresGoldHistoryAdapter(dataSource).recent(owner, 50);
        assertThat(entries)
                .extracting(
                        GoldHistoryPort.Entry::delta, GoldHistoryPort.Entry::balance, GoldHistoryPort.Entry::reasonKey)
                .containsExactly(
                        Tuple.tuple(-100L, 1150L, "gold.reason.enhance_item"),
                        Tuple.tuple(250L, 1250L, "gold.reason.sell_item"));
    }

    @Test
    void rolledBackChangesShouldLeaveNoEntry() {
        long owner = createFreshCharacter("GoldB");
        setGold(owner, 1000);

        try {
            JdbcTransactionRunner.call(dataSource, c -> {
                GoldLedger.deduct(c, owner, 300, "gold.reason.market");
                throw new IllegalStateException("rollback");
            });
        } catch (IllegalStateException expected) {
            // transaction rollback như mong đợi
        }

        assertThat(new PostgresGoldHistoryAdapter(dataSource).recent(owner, 50)).isEmpty();
        assertThat(queryLong("SELECT gold FROM character_stat WHERE character_id = ?", owner))
                .isEqualTo(1000L);
    }

    private static void setGold(long characterId, long gold) {
        queryLong("UPDATE character_stat SET gold = ? WHERE character_id = ? RETURNING gold", gold, characterId);
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

    private static long queryLong(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only truy vấn: " + sql, e);
        }
    }
}
