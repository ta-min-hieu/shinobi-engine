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
import dev.kage.guild.domain.GuildLeadershipPort;
import dev.kage.guild.domain.GuildLogPort;
import dev.kage.guild.domain.GuildRole;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-183 — nhường chức bang chủ trên Postgres thật: đổi vai hai người + {@code leader_character_id}, ghi nhật ký; từ
 * chối khi không phải bang chủ, người nhận khác bang, hay không ở bang nào.
 */
@Testcontainers
final class GuildLeadershipIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "guildleadertester"));
        accountId = accountHolder[0].id();
    }

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    void leaderHandsOver_thenOldLeaderIsJustAMember() {
        long leader = createFreshCharacter("TlLeader");
        long heir = createFreshCharacter("TlHeir");
        long outsider = createFreshCharacter("TlOutsider");
        PostgresGuildAdapter guilds = new PostgresGuildAdapter(dataSource);
        Guild guild = guilds.createGuild("TlBang", leader);
        GuildJoinRequest request = guilds.sendJoinRequest(guild.id(), heir);
        guilds.approveJoinRequest(request.id(), leader);
        PostgresGuildLeadershipAdapter adapter = new PostgresGuildLeadershipAdapter(dataSource);

        assertThat(adapter.transfer(heir, leader, NOW)).isEqualTo(GuildLeadershipPort.Outcome.NOT_LEADER);
        assertThat(adapter.transfer(leader, outsider, NOW)).isEqualTo(GuildLeadershipPort.Outcome.TARGET_NOT_IN_GUILD);
        assertThat(adapter.transfer(outsider, heir, NOW)).isEqualTo(GuildLeadershipPort.Outcome.NOT_IN_GUILD);
        assertThat(adapter.transfer(leader, heir, NOW)).isEqualTo(GuildLeadershipPort.Outcome.OK);

        assertThat(guilds.findById(guild.id()).orElseThrow().leaderCharacterId())
                .isEqualTo(heir);
        assertThat(guilds.findMembershipOf(heir).orElseThrow().role()).isEqualTo(GuildRole.LEADER);
        assertThat(guilds.findMembershipOf(leader).orElseThrow().role()).isEqualTo(GuildRole.MEMBER);
        assertThat(adapter.transfer(leader, heir, NOW)).isEqualTo(GuildLeadershipPort.Outcome.NOT_LEADER);
        assertThat(guilds.leaveGuild(leader)).isTrue(); // bang chủ cũ giờ rời bang được như thành viên thường
        List<GuildLogPort.Entry> log = new PostgresGuildLogAdapter(dataSource).recent(heir, 50);
        assertThat(log).extracting(GuildLogPort.Entry::kind).contains("LEADER_CHANGED");
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
