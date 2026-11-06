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
import dev.kage.guild.domain.GuildNoticePort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-179 — thông báo bang trên Postgres thật: bang chủ ghi, thành viên đọc, thành viên thường / người ngoài bị từ
 * chối.
 */
@Testcontainers
final class GuildNoticeIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "guildnoticetester"));
        accountId = accountHolder[0].id();
    }

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    void leaderPostsMembersRead_othersAreRefused() {
        long leader = createFreshCharacter("GnLeader");
        long member = createFreshCharacter("GnMember");
        long outsider = createFreshCharacter("GnOutsider");
        long guild = JdbcOperations.insertReturningId(
                dataSource,
                "INSERT INTO guild (name, leader_character_id, created_at) VALUES ('GnBang', ?, now()) RETURNING id",
                leader);
        join(guild, leader, "LEADER");
        join(guild, member, "MEMBER");
        PostgresGuildNoticeAdapter adapter = new PostgresGuildNoticeAdapter(dataSource);

        assertThat(adapter.noticeOf(member)).contains("");
        assertThat(adapter.set(leader, "Tối nay 8 giờ đi Bí Cảnh", NOW)).isEqualTo(GuildNoticePort.Outcome.OK);
        assertThat(adapter.noticeOf(member)).contains("Tối nay 8 giờ đi Bí Cảnh");
        assertThat(adapter.set(member, "chen ngang", NOW)).isEqualTo(GuildNoticePort.Outcome.NOT_LEADER);
        assertThat(adapter.set(outsider, "chen ngang", NOW)).isEqualTo(GuildNoticePort.Outcome.NOT_IN_GUILD);
        assertThat(adapter.noticeOf(leader)).contains("Tối nay 8 giờ đi Bí Cảnh");
        assertThat(adapter.noticeOf(outsider)).isEmpty();
    }

    private static void join(long guild, long character, String role) {
        JdbcOperations.update(
                dataSource,
                "INSERT INTO guild_member (guild_id, character_id, role, joined_at) VALUES (?, ?, ?, now())",
                guild,
                character,
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
