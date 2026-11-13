package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
import dev.kage.character.domain.LoginStreakPort;
import dev.kage.character.domain.NewCharacter;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/**
 * Lô 50 trên Postgres thật: chuỗi đăng nhập (ADR-327), người vừa chơi cùng (ADR-329), báo bị soi (ADR-330), V176.
 */
@Testcontainers
final class Lot50IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot50tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void streakCountsOncePerDayAndResetsAfterGap() {
        long character = createFreshCharacter("LsHero");
        PostgresLoginStreakAdapter adapter = new PostgresLoginStreakAdapter(dataSource);
        LocalDate day = LocalDate.of(2026, 11, 1);
        assertThat(adapter.advance(character, day))
                .map(LoginStreakPort.Row::streak)
                .contains(1);
        assertThat(adapter.advance(character, day)).isEmpty();
        assertThat(adapter.advance(character, day.plusDays(1)))
                .map(LoginStreakPort.Row::streak)
                .contains(2);
        assertThat(adapter.advance(character, day.plusDays(2)))
                .map(LoginStreakPort.Row::streak)
                .contains(3);
        LoginStreakPort.Row reset = adapter.advance(character, day.plusDays(5)).orElseThrow();
        assertThat(reset.streak()).isEqualTo(1);
        assertThat(reset.best()).isEqualTo(3);
        assertThat(adapter.find(character).orElseThrow().lastDay()).isEqualTo(day.plusDays(5));
    }

    @Test
    void recentPlayersKeepNewestOnly() {
        long me = createFreshCharacter("RpMe");
        dev.kage.server.database.party.PostgresRecentPlayerAdapter adapter =
                new dev.kage.server.database.party.PostgresRecentPlayerAdapter(dataSource);
        Instant base = Instant.parse("2026-11-13T00:00:00Z");
        long first = createFreshCharacter("RpA");
        long second = createFreshCharacter("RpB");
        long third = createFreshCharacter("RpC");
        adapter.record(me, first, base, 2);
        adapter.record(me, second, base.plusSeconds(10), 2);
        adapter.record(me, third, base.plusSeconds(20), 2);
        assertThat(adapter.recent(me, 10))
                .extracting(dev.kage.party.domain.RecentPlayerPort.Entry::otherId)
                .containsExactly(third, second);
        adapter.record(me, second, base.plusSeconds(30), 2);
        assertThat(adapter.recent(me, 10))
                .extracting(dev.kage.party.domain.RecentPlayerPort.Entry::otherId)
                .containsExactly(second, third);
    }

    @Test
    void inspectNoticeMuteToggles() {
        long character = createFreshCharacter("InHero");
        PostgresInspectNoticeAdapter adapter = new PostgresInspectNoticeAdapter(dataSource);
        assertThat(adapter.muted(character)).isFalse();
        adapter.setMuted(character, true);
        adapter.setMuted(character, true);
        assertThat(adapter.muted(character)).isTrue();
        adapter.setMuted(character, false);
        assertThat(adapter.muted(character)).isFalse();
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
