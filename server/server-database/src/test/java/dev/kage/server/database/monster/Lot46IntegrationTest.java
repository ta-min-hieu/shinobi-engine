package dev.kage.server.database.monster;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import dev.kage.mail.domain.ScheduledMailPort;
import dev.kage.monster.domain.BossKillLogPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.character.PostgresProfileLikeAdapter;
import dev.kage.server.database.mail.PostgresScheduledMailAdapter;

/** Lô 46 trên Postgres thật: lịch sử boss (ADR-296), thư hẹn giờ (ADR-297), thích hồ sơ (ADR-298), migration V173. */
@Testcontainers
final class Lot46IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot46tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void recordsAndReadsBossKills() {
        long first = createFreshCharacter("BkFirst");
        long second = createFreshCharacter("BkSecond");
        PostgresBossKillLogAdapter log = new PostgresBossKillLogAdapter(dataSource);
        log.record(List.of(
                new BossKillLogPort.Kill(first, "demon_king", BossKillLogPort.Kind.WORLD, 1, 900L),
                new BossKillLogPort.Kill(second, "demon_king", BossKillLogPort.Kind.WORLD, 2, 300L)));
        log.record(List.of(new BossKillLogPort.Kill(first, "sample_boss", BossKillLogPort.Kind.DUNGEON, 0, 0L)));

        List<BossKillLogPort.Entry> mine = log.recent(first, 20);
        assertThat(mine).hasSize(2);
        assertThat(mine.getFirst().bossCode()).isEqualTo("sample_boss");
        assertThat(mine.getFirst().kind()).isEqualTo(BossKillLogPort.Kind.DUNGEON);
        assertThat(mine.get(1).rank()).isEqualTo(1);
        assertThat(mine.get(1).damage()).isEqualTo(900L);
        assertThat(log.recent(second, 20))
                .singleElement()
                .extracting(BossKillLogPort.Entry::rank)
                .isEqualTo(2);
    }

    @Test
    void schedulesListsDueAndDeletes() {
        long sender = createFreshCharacter("SchSender");
        long recipient = createFreshCharacter("SchRecv");
        PostgresScheduledMailAdapter adapter = new PostgresScheduledMailAdapter(dataSource);
        Instant now = Instant.now();
        long due =
                adapter.schedule(new ScheduledMailPort.Pending(0L, sender, recipient, "a", "b", now.minusSeconds(5)));
        adapter.schedule(new ScheduledMailPort.Pending(0L, sender, recipient, "c", "d", now.plusSeconds(3600)));

        assertThat(adapter.pendingCount(sender)).isEqualTo(2);
        assertThat(adapter.due(now, 100))
                .extracting(ScheduledMailPort.Pending::id)
                .contains(due)
                .hasSize(1);
        adapter.delete(due);
        assertThat(adapter.pendingCount(sender)).isEqualTo(1);
    }

    @Test
    void profileLikesOncePerDay() {
        long liker = createFreshCharacter("PlLiker");
        long target = createFreshCharacter("PlTarget");
        PostgresProfileLikeAdapter adapter = new PostgresProfileLikeAdapter(dataSource);
        LocalDate today = LocalDate.of(2026, 11, 12);
        assertThat(adapter.like(liker, target, today)).isTrue();
        assertThat(adapter.like(liker, target, today)).isFalse();
        assertThat(adapter.like(liker, target, today.plusDays(1))).isTrue();
        assertThat(adapter.likesOf(target)).isEqualTo(2);
        assertThat(adapter.likesOf(liker)).isZero();
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
