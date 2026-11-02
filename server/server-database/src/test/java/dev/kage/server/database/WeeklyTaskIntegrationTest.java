package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
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
import dev.kage.checkin.domain.WeeklyTaskPort;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.checkin.PostgresWeeklyTaskAdapter;

/**
 * ADR-154 — nhiệm vụ tuần trên PostgreSQL THẬT (V125): đếm theo mã hoạt động, mỗi tuần riêng, chưa đủ thì không nhận,
 * nhận đúng một lần.
 */
@Testcontainers
class WeeklyTaskIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
    private static final LocalDate WEEK = LocalDate.of(2026, 11, 2);

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static PostgresWeeklyTaskAdapter weekly;

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
        weekly = new PostgresWeeklyTaskAdapter(dataSource);
    }

    @Test
    void shouldCountPerWeekAndPayOnceWhenDone() {
        long hero = character("NhiemVuTuan1");
        JdbcOperations.update(dataSource, "UPDATE character_stat SET gold = 0 WHERE character_id = ?", hero);
        for (int i = 0; i < 4; i++) {
            weekly.record(hero, WEEK, "dungeon_run");
        }
        weekly.record(hero, WEEK, "nothing_matches");
        weekly.record(hero, WEEK.minusWeeks(1), "dungeon_run");

        assertThat(progressOf(hero, "delver")).isEqualTo(4);
        assertThat(weekly.claim(hero, WEEK, "delver", NOW)).isEqualTo(WeeklyTaskPort.Outcome.NOT_DONE);
        weekly.record(hero, WEEK, "dungeon_run");
        assertThat(weekly.claim(hero, WEEK, "delver", NOW)).isEqualTo(WeeklyTaskPort.Outcome.OK);
        assertThat(weekly.claim(hero, WEEK, "delver", NOW)).isEqualTo(WeeklyTaskPort.Outcome.ALREADY_CLAIMED);
        assertThat(weekly.claim(hero, WEEK, "nope", NOW)).isEqualTo(WeeklyTaskPort.Outcome.UNKNOWN_TASK);
        assertThat(weekly.claim(hero, WEEK, "herbalist", NOW)).isEqualTo(WeeklyTaskPort.Outcome.NOT_DONE);

        assertThat(scalar("SELECT gold FROM character_stat WHERE character_id = ?", hero))
                .isEqualTo(4000L);
        assertThat(weekly.tasks(hero, WEEK.plusWeeks(1)))
                .as("tuần mới bắt đầu lại từ 0")
                .allSatisfy(t -> assertThat(t.progress()).isZero());
        assertThat(scalar(
                        "SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'WEEKLY_TASK_CLAIM'", hero))
                .isEqualTo(1L);
    }

    private static int progressOf(long characterId, String code) {
        return weekly.tasks(characterId, WEEK).stream()
                .filter(t -> t.code().equals(code))
                .findFirst()
                .orElseThrow()
                .progress();
    }

    private static long scalar(String sql, Object... params) {
        return JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), params)
                .orElseThrow();
    }

    private static long character(String name) {
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] account = new Account[1];
        accountTx.inTransaction(tx -> account[0] = accountAdapter.findOrCreate(tx, name.toLowerCase(Locale.ROOT)));
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(
                        account[0].id(), name, Faction.HOA, UUID.randomUUID().toString())));
        return holder[0].id();
    }
}
