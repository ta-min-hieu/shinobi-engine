package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import dev.kage.character.domain.CostumePort;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/**
 * ADR-166 — huyễn hình trang phục trên Postgres thật: chọn bậc trừ phí và ghi lại; thiếu vàng thì không đổi gì; trở về
 * theo cảnh giới miễn phí và xoá lựa chọn.
 */
@Testcontainers
final class CostumeIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant AT = Instant.parse("2026-11-03T10:00:00Z");
    private static final long FEE = 2000L;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static PostgresCostumeAdapter adapter;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "costumetester"));
        accountId = accountHolder[0].id();
        adapter = new PostgresCostumeAdapter(dataSource);
    }

    @Test
    void choosingShouldChargeAndRemember_andAutoShouldBeFreeAndClear() {
        long owner = createFreshCharacter("CosA");
        setGold(owner, FEE + 10);

        assertThat(adapter.choose(owner, 1, FEE, AT)).isEqualTo(CostumePort.Outcome.OK);
        assertThat(adapter.chosenTier(owner)).contains(1);
        assertThat(gold(owner)).isEqualTo(10L);

        assertThat(adapter.choose(owner, 0, 0L, AT)).isEqualTo(CostumePort.Outcome.OK);
        assertThat(adapter.chosenTier(owner)).isEmpty();
        assertThat(gold(owner)).isEqualTo(10L);
    }

    @Test
    void notEnoughGoldShouldChangeNothing() {
        long owner = createFreshCharacter("CosB");
        setGold(owner, FEE - 1);

        assertThat(adapter.choose(owner, 2, FEE, AT)).isEqualTo(CostumePort.Outcome.NOT_ENOUGH_GOLD);
        assertThat(adapter.chosenTier(owner)).isEmpty();
        assertThat(gold(owner)).isEqualTo(FEE - 1);
    }

    private static long gold(long characterId) {
        return queryLong("SELECT gold FROM character_stat WHERE character_id = ?", characterId);
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
