package dev.kage.server.database.guild;

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
import dev.kage.guild.domain.GuildEventPort;
import dev.kage.monster.domain.MonsterInfoPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** Lô 41 trên Postgres thật: thông tin quái + bảng rơi (ADR-255), lịch hoạt động bang (ADR-256). */
@Testcontainers
final class Lot41IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot41tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void readsSeededMonsterWithDrops() {
        MonsterInfoPort.Profile dummy = new dev.kage.server.database.monster.PostgresMonsterInfoAdapter(dataSource)
                .info("training_dummy")
                .orElseThrow();
        assertThat(dummy.maxHp()).isPositive();
        assertThat(dummy.expReward()).isPositive();
        assertThat(dummy.drops()).isNotEmpty();
        assertThat(dummy.drops().getFirst().chancePercent()).isBetween(1, 100);
        assertThat(new dev.kage.server.database.monster.PostgresMonsterInfoAdapter(dataSource).info("nope"))
                .isEmpty();
    }

    @Test
    void officersScheduleCancelAndRemindersFireOnce() {
        long leader = createFreshCharacter("EvLeader");
        long member = createFreshCharacter("EvMember");
        long guildId = insertGuild("LichBang", leader, 0L, 1);
        joinGuild(guildId, leader, "LEADER");
        joinGuild(guildId, member, "MEMBER");
        PostgresGuildEventAdapter adapter = new PostgresGuildEventAdapter(dataSource);
        Instant now = Instant.now();
        Instant soon = now.plus(Duration.ofMinutes(5));
        Instant later = now.plus(Duration.ofHours(3));

        assertThat(adapter.create(new GuildEventPort.Draft(member, "x", soon, now), 5))
                .isEqualTo(GuildEventPort.CreateOutcome.NOT_OFFICER);
        assertThat(adapter.create(new GuildEventPort.Draft(leader, "Bí cảnh", soon, now), 5))
                .isEqualTo(GuildEventPort.CreateOutcome.OK);
        assertThat(adapter.create(new GuildEventPort.Draft(leader, "Bang chiến", later, now), 2))
                .isEqualTo(GuildEventPort.CreateOutcome.OK);
        assertThat(adapter.create(new GuildEventPort.Draft(leader, "Thứ ba", later, now), 2))
                .isEqualTo(GuildEventPort.CreateOutcome.FULL);

        assertThat(adapter.upcoming(member, now))
                .extracting(GuildEventPort.Event::title)
                .containsExactly("Bí cảnh", "Bang chiến");
        assertThat(adapter.due(now, now.plus(Duration.ofMinutes(10))))
                .extracting(GuildEventPort.Due::title)
                .contains("Bí cảnh")
                .doesNotContain("Bang chiến");
        long dueId = adapter.due(now, now.plus(Duration.ofMinutes(10))).stream()
                .filter(due -> due.guildId() == guildId)
                .findFirst()
                .orElseThrow()
                .id();
        adapter.markReminded(dueId);
        assertThat(adapter.due(now, now.plus(Duration.ofMinutes(10)))).noneMatch(due -> due.id() == dueId);

        long laterId = adapter.upcoming(member, now).get(1).id();
        assertThat(adapter.cancel(member, laterId)).isEqualTo(GuildEventPort.CancelOutcome.NOT_OFFICER);
        assertThat(adapter.cancel(leader, laterId)).isEqualTo(GuildEventPort.CancelOutcome.OK);
        assertThat(adapter.cancel(leader, laterId)).isEqualTo(GuildEventPort.CancelOutcome.NOT_FOUND);
    }

    private static long insertGuild(String name, long leader, long exp, int level) {
        return dev.kage.server.database.JdbcOperations.queryOne(
                        dataSource,
                        "INSERT INTO guild (name, leader_character_id, created_at, exp, level)"
                                + " VALUES (?, ?, now(), ?, ?) RETURNING id",
                        rs -> rs.getLong(1),
                        name,
                        leader,
                        exp,
                        level)
                .orElseThrow();
    }

    private static void joinGuild(long guildId, long characterId, String role) {
        dev.kage.server.database.JdbcOperations.update(
                dataSource,
                "INSERT INTO guild_member (guild_id, character_id, role, joined_at) VALUES (?, ?, ?, now())",
                guildId,
                characterId,
                role);
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
