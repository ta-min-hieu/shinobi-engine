package dev.kage.server.database.guild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.guild.domain.DuplicateGuildJoinRequestException;
import dev.kage.guild.domain.DuplicateGuildNameException;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildJoinRequest;
import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;
import dev.kage.guild.domain.GuildRole;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V14 ({@code guild}/{@code
 * guild_member}/{@code guild_join_request}) + tính NGUYÊN TỬ của {@code createGuild}/
 * {@code approveJoinRequest} (feature-guild/CLAUDE.md).
 */
@Testcontainers
class GuildIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "guildtester"));
        accountId = accountHolder[0].id();
    }

    private static long createFreshCharacter(String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    private static PostgresGuildAdapter newAdapter() {
        return new PostgresGuildAdapter(dataSource);
    }

    @Test
    void shouldCreateGuild_andMakeCreatorLeaderMember() {
        long leader = createFreshCharacter("GuildLeader1");
        PostgresGuildAdapter adapter = newAdapter();

        Guild guild = adapter.createGuild("Konoha1", leader);

        assertThat(adapter.findByName("Konoha1")).isPresent();
        GuildMember membership = adapter.findMembershipOf(leader).orElseThrow();
        assertThat(membership.guildId()).isEqualTo(guild.id());
        assertThat(membership.role()).isEqualTo(GuildRole.LEADER);
    }

    @Test
    void shouldRejectDuplicateGuildName() {
        long leaderA = createFreshCharacter("GuildLeader2");
        long leaderB = createFreshCharacter("GuildLeader3");
        PostgresGuildAdapter adapter = newAdapter();
        adapter.createGuild("Konoha2", leaderA);

        assertThatThrownBy(() -> adapter.createGuild("Konoha2", leaderB))
                .isInstanceOf(DuplicateGuildNameException.class);
        // 2 bảng phải nguyên tử — thất bại ở guild KHÔNG được để lại rác guild_member cho leaderB.
        assertThat(adapter.findMembershipOf(leaderB)).isEmpty();
    }

    @Test
    void shouldApproveJoinRequest_andAddMemberAtomically() {
        long leader = createFreshCharacter("GuildLeader4");
        long applicant = createFreshCharacter("GuildApplicant1");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guild = adapter.createGuild("Konoha3", leader);
        GuildJoinRequest request = adapter.sendJoinRequest(guild.id(), applicant);

        GuildPort.ApproveResult result = adapter.approveJoinRequest(request.id(), leader);

        assertThat(result).isEqualTo(GuildPort.ApproveResult.OK);
        assertThat(adapter.findMembershipOf(applicant)).isPresent();
        assertThat(adapter.findPendingRequestOfCharacter(applicant)).isEmpty();
    }

    @Test
    void shouldRejectApprove_whenCallerIsNotLeader() {
        long leader = createFreshCharacter("GuildLeader5");
        long notLeader = createFreshCharacter("GuildNotLeader1");
        long applicant = createFreshCharacter("GuildApplicant2");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guild = adapter.createGuild("Konoha4", leader);
        GuildJoinRequest request = adapter.sendJoinRequest(guild.id(), applicant);

        GuildPort.ApproveResult result = adapter.approveJoinRequest(request.id(), notLeader);

        assertThat(result).isEqualTo(GuildPort.ApproveResult.NOT_FOUND);
        assertThat(adapter.findMembershipOf(applicant)).isEmpty();
    }

    @Test
    void shouldRejectDuplicateJoinRequest_sameGuildSameCharacter() {
        long leader = createFreshCharacter("GuildLeader6");
        long applicant = createFreshCharacter("GuildApplicant3");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guild = adapter.createGuild("Konoha5", leader);
        adapter.sendJoinRequest(guild.id(), applicant);

        assertThatThrownBy(() -> adapter.sendJoinRequest(guild.id(), applicant))
                .isInstanceOf(DuplicateGuildJoinRequestException.class);
    }

    @Test
    void shouldDropStaleRequest_whenApplicantAlreadyInAnotherGuildAtApprovalTime() {
        long leaderA = createFreshCharacter("GuildLeader7");
        long applicant = createFreshCharacter("GuildApplicant4");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guildA = adapter.createGuild("Konoha6", leaderA);
        GuildJoinRequest request = adapter.sendJoinRequest(guildA.id(), applicant);
        adapter.createGuild("Suna1", applicant); // applicant tự tạo bang khác trong lúc đơn PENDING

        GuildPort.ApproveResult result = adapter.approveJoinRequest(request.id(), leaderA);

        assertThat(result).isEqualTo(GuildPort.ApproveResult.TARGET_ALREADY_IN_GUILD);
        assertThat(adapter.findPendingRequestOfCharacter(applicant)).isEmpty();
    }

    @Test
    void shouldDisbandGuild_andCascadeDeleteMembersAndRequests() {
        long leader = createFreshCharacter("GuildLeader8");
        long member = createFreshCharacter("GuildMember1");
        long applicant = createFreshCharacter("GuildApplicant5");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guild = adapter.createGuild("Konoha7", leader);
        adapter.approveJoinRequest(adapter.sendJoinRequest(guild.id(), member).id(), leader);
        adapter.sendJoinRequest(guild.id(), applicant);

        boolean disbanded = adapter.disbandGuild(leader);

        assertThat(disbanded).isTrue();
        assertThat(adapter.findById(guild.id())).isEmpty();
        assertThat(adapter.findMembershipOf(leader)).isEmpty();
        assertThat(adapter.findMembershipOf(member)).isEmpty();
        assertThat(adapter.findPendingRequestOfCharacter(applicant)).isEmpty();
    }

    @Test
    void shouldEnforce1GuildPerCharacter_atDatabaseLevel() {
        long leaderA = createFreshCharacter("GuildLeader9");
        long leaderB = createFreshCharacter("GuildLeader10");
        long applicant = createFreshCharacter("GuildApplicant6");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guildA = adapter.createGuild("Konoha8", leaderA);
        Guild guildB = adapter.createGuild("Suna2", leaderB);
        GuildJoinRequest requestToA = adapter.sendJoinRequest(guildA.id(), applicant);
        adapter.approveJoinRequest(requestToA.id(), leaderA);
        GuildJoinRequest requestToB = adapter.sendJoinRequest(guildB.id(), applicant);

        GuildPort.ApproveResult result = adapter.approveJoinRequest(requestToB.id(), leaderB);

        assertThat(result).isEqualTo(GuildPort.ApproveResult.TARGET_ALREADY_IN_GUILD);
    }
}
