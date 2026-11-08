package dev.kage.server.database.guild;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
import dev.kage.guild.domain.GuildPollPort;
import dev.kage.inventory.domain.ItemHistoryPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** Lô 40 trên Postgres thật: bình chọn trong bang (ADR-248), lịch sử nhặt đồ đọc audit (ADR-253). */
@Testcontainers
final class Lot40IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot40tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void officersOpenPollsMembersVoteAndChangeVotes() {
        long leader = createFreshCharacter("PlLeader");
        long member = createFreshCharacter("PlMember");
        long outsider = createFreshCharacter("PlOutsider");
        long guildId = insertGuild("PollBang", leader, 0L, 1);
        joinGuild(guildId, leader, "LEADER");
        joinGuild(guildId, member, "MEMBER");
        PostgresGuildPollAdapter adapter = new PostgresGuildPollAdapter(dataSource);
        Instant now = Instant.now();
        Instant closes = now.plus(Duration.ofHours(24));

        assertThat(adapter.vote(member, 0, now)).isEqualTo(GuildPollPort.VoteOutcome.NO_OPEN_POLL);
        assertThat(adapter.create(new GuildPollPort.Draft(member, "Q", List.of("a", "b"), closes, now)))
                .isEqualTo(GuildPollPort.CreateOutcome.NOT_OFFICER);
        assertThat(adapter.create(new GuildPollPort.Draft(outsider, "Q", List.of("a", "b"), closes, now)))
                .isEqualTo(GuildPollPort.CreateOutcome.NOT_IN_GUILD);
        assertThat(adapter.create(
                        new GuildPollPort.Draft(leader, "Đi bí cảnh?", List.of("Có", "Không", "Mai"), closes, now)))
                .isEqualTo(GuildPollPort.CreateOutcome.OK);
        assertThat(adapter.vote(member, 3, now)).isEqualTo(GuildPollPort.VoteOutcome.BAD_OPTION);
        assertThat(adapter.vote(member, 0, now)).isEqualTo(GuildPollPort.VoteOutcome.OK);
        assertThat(adapter.vote(leader, 0, now)).isEqualTo(GuildPollPort.VoteOutcome.OK);
        assertThat(adapter.vote(member, 2, now)).isEqualTo(GuildPollPort.VoteOutcome.OK);

        GuildPollPort.Poll poll = adapter.latest(member).orElseThrow();
        assertThat(poll.question()).isEqualTo("Đi bí cảnh?");
        assertThat(poll.options()).containsExactly("Có", "Không", "Mai");
        assertThat(poll.votes()).containsExactly(1, 0, 1);
        assertThat(poll.myVote()).isEqualTo(2);
        assertThat(adapter.latest(outsider)).isEmpty();

        assertThat(adapter.create(
                        new GuildPollPort.Draft(leader, "Mới", List.of("x", "y"), closes, now.plusSeconds(1))))
                .isEqualTo(GuildPollPort.CreateOutcome.OK);
        assertThat(adapter.latest(member).orElseThrow().votes()).containsExactly(0, 0);
        assertThat(single(
                        "SELECT COUNT(*) FROM guild_poll WHERE guild_id = ? AND closes_at > now() + interval '1 hour'",
                        guildId))
                .isEqualTo(1L);
    }

    @Test
    void readsLootGrantsNewestFirst() {
        long owner = createFreshCharacter("LtOwner");
        for (int i = 1; i <= 3; i++) {
            dev.kage.server.database.JdbcOperations.update(
                    dataSource,
                    "INSERT INTO audit_log (character_id, action, detail_json, created_at)"
                            + " VALUES (?, 'LOOT_GRANT', ?, now())",
                    owner,
                    "{\"source\":\"training_dummy\",\"itemTemplateId\":1,\"quantity\":" + i + "}");
        }
        List<ItemHistoryPort.LootRecord> loot =
                new dev.kage.server.database.inventory.PostgresItemHistoryAdapter(dataSource).lootHistory(owner, 2);

        assertThat(loot).extracting(ItemHistoryPort.LootRecord::quantity).containsExactly(3, 2);
        assertThat(loot.getFirst().source()).isEqualTo("training_dummy");
        assertThat(loot.getFirst().itemTemplateId()).isEqualTo(1L);
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

    private static long single(String sql, Object arg) {
        return dev.kage.server.database.JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), arg)
                .orElseThrow();
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
