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
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildRecruitmentPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-185 — bảng chiêu mộ trên Postgres thật: chỉ bang chủ / phó treo được, bảng đếm người, gỡ được, tra cấp tối thiểu.
 */
@Testcontainers
final class GuildRecruitmentIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "guildrecruittester"));
        accountId = accountHolder[0].id();
    }

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    void leaderOrVicePosts_boardListsAndTakesDown() {
        long leader = createFreshCharacter("RcLeader");
        long member = createFreshCharacter("RcMember");
        long outsider = createFreshCharacter("RcOutsider");
        PostgresGuildAdapter guilds = new PostgresGuildAdapter(dataSource);
        Guild guild = guilds.createGuild("RcBang", leader);
        guilds.approveJoinRequest(guilds.sendJoinRequest(guild.id(), member).id(), leader);
        PostgresGuildRecruitmentAdapter adapter = new PostgresGuildRecruitmentAdapter(dataSource);

        assertThat(adapter.set(outsider, 10, "x", NOW)).isEqualTo(GuildRecruitmentPort.Outcome.NOT_IN_GUILD);
        assertThat(adapter.set(member, 10, "x", NOW)).isEqualTo(GuildRecruitmentPort.Outcome.NOT_ALLOWED);
        assertThat(adapter.set(leader, 15, "Tuyển đệ tử chăm chỉ", NOW)).isEqualTo(GuildRecruitmentPort.Outcome.OK);
        assertThat(adapter.minLevelOf("RcBang")).hasValue(15);
        assertThat(adapter.list(50))
                .filteredOn(l -> l.guildName().equals("RcBang"))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.members()).isEqualTo(2);
                    assertThat(l.message()).isEqualTo("Tuyển đệ tử chăm chỉ");
                });
        new PostgresGuildLeadershipAdapter(dataSource).setViceLeader(leader, member, true, NOW);
        assertThat(adapter.set(member, 20, "Phó sửa", NOW)).isEqualTo(GuildRecruitmentPort.Outcome.OK);
        assertThat(adapter.minLevelOf("RcBang")).hasValue(20);
        assertThat(adapter.set(leader, 1, null, NOW)).isEqualTo(GuildRecruitmentPort.Outcome.OK);
        assertThat(adapter.minLevelOf("RcBang")).isEmpty();
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
