package dev.kage.server.database.monster;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import dev.kage.monster.domain.BossKillLogPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.character.PostgresProfileLikeAdapter;

/** Lô 47 trên Postgres thật: xếp hạng sát thương boss (ADR-303), xếp hạng lượt thích (ADR-305). */
@Testcontainers
final class Lot47IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot47tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void ranksWorldBossDamageAcrossKills() {
        long strong = createFreshCharacter("RkStrong");
        long weak = createFreshCharacter("RkWeak");
        PostgresBossKillLogAdapter log = new PostgresBossKillLogAdapter(dataSource);
        log.record(List.of(
                new BossKillLogPort.Kill(strong, "demon_king", BossKillLogPort.Kind.WORLD, 1, 5_000_000L),
                new BossKillLogPort.Kill(weak, "demon_king", BossKillLogPort.Kind.WORLD, 2, 4_000_000L)));
        log.record(List.of(
                new BossKillLogPort.Kill(strong, "demon_king", BossKillLogPort.Kind.WORLD, 1, 6_000_000L),
                new BossKillLogPort.Kill(weak, "sample_boss", BossKillLogPort.Kind.DUNGEON, 0, 0L)));

        List<BossKillLogPort.Ranked> top = log.topWorldDamage(10);
        assertThat(top.getFirst().name()).isEqualTo("RkStrong");
        assertThat(top.getFirst().totalDamage()).isEqualTo(11_000_000L);
        assertThat(top.getFirst().kills()).isEqualTo(2);
        assertThat(top)
                .filteredOn(r -> r.name().equals("RkWeak"))
                .singleElement()
                .extracting(BossKillLogPort.Ranked::kills)
                .isEqualTo(1);
    }

    @Test
    void ranksProfileLikes() {
        long star = createFreshCharacter("LkStar");
        long fan1 = createFreshCharacter("LkFanA");
        long fan2 = createFreshCharacter("LkFanB");
        PostgresProfileLikeAdapter adapter = new PostgresProfileLikeAdapter(dataSource);
        LocalDate day = LocalDate.of(2026, 11, 13);
        adapter.like(fan1, star, day);
        adapter.like(fan2, star, day);
        adapter.like(star, fan1, day);
        List<dev.kage.character.domain.ProfileLikePort.Ranked> top = adapter.top(10);
        assertThat(top.getFirst().name()).isEqualTo("LkStar");
        assertThat(top.getFirst().likes()).isEqualTo(2);
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
