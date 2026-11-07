package dev.kage.server.database.guild;

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
import dev.kage.character.domain.NewCharacter;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildCheckInPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-186 — điểm danh bang trên Postgres thật: một lần / nhân vật / ngày, cộng cống hiến + kinh nghiệm bang, đếm theo
 * bang.
 */
@Testcontainers
final class GuildCheckInIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "guildcheckintester"));
        accountId = accountHolder[0].id();
    }

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    void oncePerDay_addsContributionAndGuildExp() {
        long leader = createFreshCharacter("CkLeader");
        long member = createFreshCharacter("CkMember");
        long outsider = createFreshCharacter("CkOutsider");
        PostgresGuildAdapter guilds = new PostgresGuildAdapter(dataSource);
        Guild guild = guilds.createGuild("CkBang", leader);
        guilds.approveJoinRequest(guilds.sendJoinRequest(guild.id(), member).id(), leader);
        PostgresGuildCheckInAdapter adapter = new PostgresGuildCheckInAdapter(dataSource);
        LocalDate today = LocalDate.of(2026, 11, 7);

        assertThat(adapter.checkIn(outsider, today, NOW).outcome()).isEqualTo(GuildCheckInPort.Outcome.NOT_IN_GUILD);
        GuildCheckInPort.CheckIn first = adapter.checkIn(leader, today, NOW);
        assertThat(first.outcome()).isEqualTo(GuildCheckInPort.Outcome.OK);
        assertThat(first.todayCount()).isEqualTo(1);
        assertThat(adapter.checkIn(leader, today, NOW).outcome())
                .isEqualTo(GuildCheckInPort.Outcome.ALREADY_CHECKED_IN);
        assertThat(adapter.checkIn(member, today, NOW).todayCount()).isEqualTo(2);
        assertThat(adapter.checkIn(leader, today.plusDays(1), NOW).outcome()).isEqualTo(GuildCheckInPort.Outcome.OK);

        long contribution = dev.kage.server.database.JdbcOperations.queryOne(
                        dataSource,
                        "SELECT contribution FROM guild_member WHERE character_id = ?",
                        rs -> rs.getLong(1),
                        leader)
                .orElseThrow();
        long exp = dev.kage.server.database.JdbcOperations.queryOne(
                        dataSource, "SELECT exp FROM guild WHERE id = ?", rs -> rs.getLong(1), guild.id())
                .orElseThrow();
        assertThat(contribution).isEqualTo(2 * GuildCheckInPort.CONTRIBUTION);
        assertThat(exp).isEqualTo(3 * GuildCheckInPort.GUILD_EXP);
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
