package dev.kage.server.database.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.party.domain.DuplicatePartyInviteException;
import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyInvite;
import dev.kage.party.domain.PartyMember;
import dev.kage.party.domain.PartyPort;
import dev.kage.party.domain.PartyRole;
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
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V15 ({@code party}/{@code
 * party_member}/{@code party_invite}) + tính NGUYÊN TỬ của {@code createParty}/
 * {@code acceptInvite}/{@code leaveParty} (feature-party/CLAUDE.md).
 */
@Testcontainers
class PartyIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "partytester"));
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

    private static PostgresPartyAdapter newAdapter() {
        return new PostgresPartyAdapter(dataSource);
    }

    @Test
    void shouldCreateParty_andMakeCreatorLeaderMember() {
        long leader = createFreshCharacter("PartyLeader1");
        PostgresPartyAdapter adapter = newAdapter();

        Party party = adapter.createParty(leader);

        PartyMember membership = adapter.findMembershipOf(leader).orElseThrow();
        assertThat(membership.partyId()).isEqualTo(party.id());
        assertThat(membership.role()).isEqualTo(PartyRole.LEADER);
    }

    @Test
    void shouldAcceptInvite_andAddMemberAtomically() {
        long leader = createFreshCharacter("PartyLeader2");
        long invitee = createFreshCharacter("PartyInvitee1");
        PostgresPartyAdapter adapter = newAdapter();
        Party party = adapter.createParty(leader);
        PartyInvite invite = adapter.invite(party.id(), invitee);

        PartyPort.AcceptResult result = adapter.acceptInvite(invite.id(), invitee);

        assertThat(result).isEqualTo(PartyPort.AcceptResult.OK);
        assertThat(adapter.findMembershipOf(invitee)).isPresent();
        assertThat(adapter.findPendingInviteOfInvitee(invitee)).isEmpty();
    }

    @Test
    void shouldRejectAccept_whenCallerIsNotTheInvitee() {
        long leader = createFreshCharacter("PartyLeader3");
        long invitee = createFreshCharacter("PartyInvitee2");
        long stranger = createFreshCharacter("PartyStranger1");
        PostgresPartyAdapter adapter = newAdapter();
        Party party = adapter.createParty(leader);
        PartyInvite invite = adapter.invite(party.id(), invitee);

        PartyPort.AcceptResult result = adapter.acceptInvite(invite.id(), stranger);

        assertThat(result).isEqualTo(PartyPort.AcceptResult.NOT_FOUND);
        assertThat(adapter.findMembershipOf(invitee)).isEmpty();
    }

    @Test
    void shouldRejectDuplicateInvite_sameParty() {
        long leader = createFreshCharacter("PartyLeader4");
        long invitee = createFreshCharacter("PartyInvitee3");
        PostgresPartyAdapter adapter = newAdapter();
        Party party = adapter.createParty(leader);
        adapter.invite(party.id(), invitee);

        assertThatThrownBy(() -> adapter.invite(party.id(), invitee))
                .isInstanceOf(DuplicatePartyInviteException.class);
    }

    @Test
    void shouldReturnAlreadyInParty_andDropStaleInvite_whenInviteeJoinedElsewhereMeanwhile() {
        long leaderA = createFreshCharacter("PartyLeader5");
        long invitee = createFreshCharacter("PartyInvitee4");
        PostgresPartyAdapter adapter = newAdapter();
        Party partyA = adapter.createParty(leaderA);
        PartyInvite invite = adapter.invite(partyA.id(), invitee);
        adapter.createParty(invitee); // invitee tự tạo party khác trong lúc lời mời còn PENDING

        PartyPort.AcceptResult result = adapter.acceptInvite(invite.id(), invitee);

        assertThat(result).isEqualTo(PartyPort.AcceptResult.ALREADY_IN_PARTY);
        assertThat(adapter.findPendingInviteOfInvitee(invitee)).isEmpty();
    }

    @Test
    void shouldRejectAccept_whenPartyAlreadyAtMaxCapacity() {
        long leader = createFreshCharacter("PartyLeader6");
        PostgresPartyAdapter adapter = newAdapter();
        Party party = adapter.createParty(leader);
        for (int i = 0; i < Party.MAX_MEMBERS - 1; i++) {
            long memberId = createFreshCharacter("PartyFill" + i);
            adapter.acceptInvite(adapter.invite(party.id(), memberId).id(), memberId);
        }
        long lateInvitee = createFreshCharacter("PartyLateInv1");
        PartyInvite lateInvite = adapter.invite(party.id(), lateInvitee);

        PartyPort.AcceptResult result = adapter.acceptInvite(lateInvite.id(), lateInvitee);

        assertThat(result).isEqualTo(PartyPort.AcceptResult.PARTY_FULL);
        assertThat(adapter.listMembers(party.id())).hasSize(Party.MAX_MEMBERS);
    }

    @Test
    void shouldPromoteEarliestRemainingMember_whenLeaderLeaves() {
        long leader = createFreshCharacter("PartyLeader7");
        long memberA = createFreshCharacter("PartyMemberA1");
        long memberB = createFreshCharacter("PartyMemberB1");
        PostgresPartyAdapter adapter = newAdapter();
        Party party = adapter.createParty(leader);
        adapter.acceptInvite(adapter.invite(party.id(), memberA).id(), memberA);
        adapter.acceptInvite(adapter.invite(party.id(), memberB).id(), memberB);

        PartyPort.LeaveResult result = adapter.leaveParty(leader);

        assertThat(result).isEqualTo(PartyPort.LeaveResult.OK);
        assertThat(adapter.findMembershipOf(leader)).isEmpty();
        assertThat(adapter.findMembershipOf(memberA).orElseThrow().role()).isEqualTo(PartyRole.LEADER);
        assertThat(adapter.findMembershipOf(memberB).orElseThrow().role()).isEqualTo(PartyRole.MEMBER);
    }

    @Test
    void shouldDissolveParty_whenLastMemberLeaves() {
        long leader = createFreshCharacter("PartyLeader8");
        PostgresPartyAdapter adapter = newAdapter();
        adapter.createParty(leader);

        PartyPort.LeaveResult result = adapter.leaveParty(leader);

        assertThat(result).isEqualTo(PartyPort.LeaveResult.OK);
        assertThat(adapter.findMembershipOf(leader)).isEmpty();
    }

    @Test
    void shouldKickMember_whenCallerIsLeader() {
        long leader = createFreshCharacter("PartyLeader9");
        long member = createFreshCharacter("PartyMemberC1");
        PostgresPartyAdapter adapter = newAdapter();
        Party party = adapter.createParty(leader);
        adapter.acceptInvite(adapter.invite(party.id(), member).id(), member);

        boolean kicked = adapter.kickMember(leader, member);

        assertThat(kicked).isTrue();
        assertThat(adapter.findMembershipOf(member)).isEmpty();
    }
}
