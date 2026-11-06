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
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildJoinRequest;
import dev.kage.guild.domain.GuildLogPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
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
final class GuildLogIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "guildlogtester"));
        accountId = accountHolder[0].id();
    }

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    void everyMembershipChangeWritesOneRow_newestFirst() {
        long leader = createFreshCharacter("GlLeader");
        long stays = createFreshCharacter("GlStays");
        long kicked = createFreshCharacter("GlKicked");
        long outsider = createFreshCharacter("GlOutsider");
        PostgresGuildAdapter guilds = new PostgresGuildAdapter(dataSource);
        Guild guild = guilds.createGuild("GlBang", leader);
        for (long member : new long[] {stays, kicked}) {
            GuildJoinRequest request = guilds.sendJoinRequest(guild.id(), member);
            guilds.approveJoinRequest(request.id(), leader);
        }
        new PostgresGuildNoticeAdapter(dataSource).set(leader, "Họp bang", NOW);
        assertThat(guilds.kickMember(leader, kicked)).isTrue();
        assertThat(guilds.kickMember(leader, kicked)).isFalse(); // đã ra rồi — không ghi dòng thứ hai
        assertThat(guilds.leaveGuild(leader)).isFalse(); // bang chủ không "rời" được — không ghi gì

        List<GuildLogPort.Entry> log = new PostgresGuildLogAdapter(dataSource).recent(stays, 50);

        assertThat(log)
                .extracting(GuildLogPort.Entry::kind)
                .containsExactlyInAnyOrder("KICKED", "NOTICE_CHANGED", "JOINED", "JOINED", "CREATED");
        assertThat(log)
                .filteredOn(e -> e.kind().equals("KICKED"))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.actorName()).isEqualTo("GlLeader");
                    assertThat(e.subjectName()).isEqualTo("GlKicked");
                });
        assertThat(guilds.leaveGuild(stays)).isTrue();
        assertThat(new PostgresGuildLogAdapter(dataSource)
                        .recent(leader, 50)
                        .get(0)
                        .kind())
                .isEqualTo("LEFT");
        assertThat(new PostgresGuildLogAdapter(dataSource).recent(outsider, 50)).isEmpty();
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
