package dev.kage.server.database;

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
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.world.PostgresKillingIntentAdapter;
import dev.kage.world.domain.KillingIntentRules;

/** ADR-142 — sát khí trên PostgreSQL THẬT (V115): cộng điểm, phân rã, Ma Đầu, vàng rơi về người hạ. */
@Testcontainers
class KillingIntentIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

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
    }

    @Test
    void shouldBecomeAnOutlawAtThreeKills_andFadeAfterwards() {
        long killer = character("satkhi_a");
        PostgresKillingIntentAdapter adapter = new PostgresKillingIntentAdapter(dataSource);

        adapter.recordKill(killer, NOW);
        adapter.recordKill(killer, NOW);
        assertThat(adapter.outlaws(NOW)).doesNotContain(killer);
        assertThat(adapter.recordKill(killer, NOW)).isEqualTo(KillingIntentRules.OUTLAW_POINTS);

        assertThat(adapter.outlaws(NOW)).contains(killer);
        Instant later = NOW.plus(KillingIntentRules.DECAY_PER_POINT);
        assertThat(adapter.points(killer, later)).isEqualTo(2);
        assertThat(adapter.outlaws(later)).doesNotContain(killer);
    }

    @Test
    void shouldDropTenPercentOfAnOutlawsGold_toTheKiller() {
        long outlaw = character("satkhi_b");
        long hunter = character("satkhi_c");
        PostgresKillingIntentAdapter adapter = new PostgresKillingIntentAdapter(dataSource);
        setGold(outlaw, 8_000L);
        setGold(hunter, 100L);
        for (int i = 0; i < KillingIntentRules.OUTLAW_POINTS; i++) {
            adapter.recordKill(outlaw, NOW);
        }

        assertThat(adapter.dropGold(outlaw, hunter, NOW)).isEqualTo(800L);

        assertThat(gold(outlaw)).isEqualTo(7_200L);
        assertThat(gold(hunter)).isEqualTo(900L);
    }

    @Test
    void shouldDropNothing_forAnOrdinaryPlayer() {
        long ordinary = character("satkhi_d");
        long other = character("satkhi_e");
        setGold(ordinary, 8_000L);

        assertThat(new PostgresKillingIntentAdapter(dataSource).dropGold(ordinary, other, NOW))
                .isZero();

        assertThat(gold(ordinary)).isEqualTo(8_000L);
    }

    private static long character(String name) {
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        Account[] account = new Account[1];
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> account[0] = accountAdapter.findOrCreate(tx, name));
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        Character[] holder = new Character[1];
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(t -> holder[0] = adapter.create(
                        t,
                        new NewCharacter(
                                account[0].id(),
                                name,
                                Faction.HOA,
                                UUID.randomUUID().toString())));
        return holder[0].id();
    }

    private static void setGold(long characterId, long gold) {
        JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = ? WHERE character_id = ?", gold, characterId);
    }

    private static long gold(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        "SELECT gold FROM character_stat WHERE character_id = ?",
                        rs -> rs.getLong(1),
                        characterId)
                .orElseThrow();
    }
}
