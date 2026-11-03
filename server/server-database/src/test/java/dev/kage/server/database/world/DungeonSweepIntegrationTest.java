package dev.kage.server.database.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
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
import dev.kage.world.domain.DungeonSweepPort;

/**
 * ADR-162 — quét nhanh Bí Cảnh trên Postgres thật: chưa hạ boss thì từ chối; đã hạ thì trừ vàng + một lượt ngày + cộng
 * EXP; hết lượt (chung với lượt vào thật) thì từ chối; thiếu vàng thì không mất lượt.
 */
@Testcontainers
final class DungeonSweepIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final String DUNGEON = "sample_dungeon";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);
    private static final int MAX = 3;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static PostgresDungeonSweepAdapter adapter;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "sweeptester"));
        accountId = accountHolder[0].id();
        adapter = new PostgresDungeonSweepAdapter(dataSource);
    }

    @Test
    void sweepShouldRequireAFirstClear() {
        long owner = createFreshCharacter("SwpA");
        setGold(owner, 5000);

        assertThat(adapter.sweep(owner, DUNGEON, DAY, MAX).outcome()).isEqualTo(DungeonSweepPort.Outcome.NOT_CLEARED);
        assertThat(gold(owner)).isEqualTo(5000L);
    }

    @Test
    void sweepShouldChargeGoldUseARunAndGrantExp_untilRunsAreGone() {
        long owner = createFreshCharacter("SwpB");
        new PostgresDungeonQuotaAdapter(dataSource).recordFirstClear(owner, DUNGEON);
        setGold(owner, 5000);
        long expBefore = queryLong("SELECT exp FROM character WHERE id = ?", owner);

        DungeonSweepPort.Result first = adapter.sweep(owner, DUNGEON, DAY, MAX);

        assertThat(first.outcome()).isEqualTo(DungeonSweepPort.Outcome.OK);
        assertThat(first.dropTableCode()).isEqualTo("sample_boss");
        assertThat(first.entriesUsed()).isEqualTo(1);
        assertThat(gold(owner)).isEqualTo(4000L);
        assertThat(queryLong("SELECT exp FROM character WHERE id = ?", owner)).isEqualTo(expBefore + first.expGained());
        adapter.sweep(owner, DUNGEON, DAY, MAX);
        adapter.sweep(owner, DUNGEON, DAY, MAX);
        assertThat(adapter.sweep(owner, DUNGEON, DAY, MAX).outcome())
                .isEqualTo(DungeonSweepPort.Outcome.DAILY_LIMIT_REACHED);
        assertThat(gold(owner)).isEqualTo(2000L);
    }

    @Test
    void notEnoughGoldShouldKeepTheRun() {
        long owner = createFreshCharacter("SwpC");
        new PostgresDungeonQuotaAdapter(dataSource).recordFirstClear(owner, DUNGEON);
        setGold(owner, 999);

        assertThat(adapter.sweep(owner, DUNGEON, DAY, MAX).outcome())
                .isEqualTo(DungeonSweepPort.Outcome.NOT_ENOUGH_GOLD);
        assertThat(new PostgresDungeonQuotaAdapter(dataSource).usedOn(owner, DUNGEON, DAY))
                .isZero();
        assertThat(adapter.sweep(owner, "no_such_dungeon", DAY, MAX).outcome())
                .isEqualTo(DungeonSweepPort.Outcome.UNKNOWN_DUNGEON);
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
