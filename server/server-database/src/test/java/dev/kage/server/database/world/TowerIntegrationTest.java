package dev.kage.server.database.world;

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
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.world.domain.TowerPort;

/** ADR-113 trên PostgreSQL THẬT (V93) — tầng là dữ liệu, ghi tầng chỉ tăng, thưởng mốc trao đúng một lần. */
@Testcontainers
class TowerIntegrationTest {

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
        Account[] holder = new Account[1];
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "towertester"));
        accountId = holder[0].id();
    }

    private static long character(String name, long gold) {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString())));
        JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = ? WHERE character_id = ?", gold, holder[0].id());
        return holder[0].id();
    }

    @Test
    void floorsShouldBeDataAndBestFloorShouldOnlyRise() {
        PostgresTowerAdapter tower = new PostgresTowerAdapter(dataSource);
        long climber = character("TowerA1", 0L);

        assertThat(tower.topFloor()).isEqualTo(20);
        assertThat(tower.floor(1)).contains(new TowerPort.Floor(1, "training_dummy", 2));
        assertThat(tower.floor(21)).isEmpty();
        assertThat(tower.bestFloorOf(climber)).isZero();

        for (int floor = 1; floor <= 4; floor++) {
            assertThat(tower.recordCleared(climber, floor)).isEmpty();
        }
        TowerPort.Reward reward = tower.recordCleared(climber, 5).orElseThrow();
        assertThat(reward.gold()).isEqualTo(1000L);
        assertThat(reward.quantity()).isEqualTo(2);
        assertThat(tower.recordCleared(climber, 5)).isEmpty();
        assertThat(tower.recordCleared(climber, 3)).isEmpty();
        assertThat(tower.bestFloorOf(climber)).isEqualTo(5);
        assertThat(tower.top(10)).contains(new TowerPort.Climber("TowerA1", 5));
    }
}
