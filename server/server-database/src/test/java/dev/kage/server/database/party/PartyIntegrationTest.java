package dev.kage.server.database.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

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

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V15 ({@code party}/{@code
 * party_member}/{@code party_invite}) + tính NGUYÊN TỬ của {@code createParty}/
 * {@code acceptInvite}/{@code leaveParty} (feature-party/CLAUDE.md).
 */
@Testcontainers
class PartyIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "partytester"));
        accountId = accountHolder[0].id();
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

        assertThatThrownBy(() -> adapter.invite(party.id(), invitee)).isInstanceOf(DuplicatePartyInviteException.class);
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

    /**
     * ⚠️ Race THẬT — {@code acceptInvite} khoá dòng {@code party} bằng {@code SELECT ... FOR UPDATE}
     * (đúng như javadoc {@link dev.kage.party.domain.PartyPort#acceptInvite} mô tả), NHƯNG bỏ qua
     * việc dòng đó có còn tồn tại hay không: {@code leaveParty} khoá CÙNG dòng {@code party} này khi
     * leader (người cuối cùng) rời nhóm và GIẢI TÁN — {@code DELETE FROM party}. Nếu 1 lời mời đang
     * PENDING đúng lúc leader rời, {@code acceptInvite} vẫn chạy tiếp sau khi khoá "trúng" 0 dòng,
     * rồi INSERT {@code party_member} tham chiếu 1 {@code party_id} không còn tồn tại → vi phạm FOREIGN
     * KEY, ném {@code DatabaseAccessException} THẲNG ra ngoài thay vì trả kết quả gọn (client bấm
     * "Chấp nhận" sẽ treo vô thời hạn — {@code GatewayChannelHandler.handleAcceptPartyInviteBlocking}
     * không có try/catch nên không response nào được gửi khi use case này ném lỗi).
     *
     * <p>Test tự tay giữ khoá {@code FOR UPDATE} trên dòng {@code party} bằng 1 kết nối JDBC riêng
     * (cùng kỹ thuật {@code PostgresPvpRatingAdapterIntegrationTest}) để buộc lời gọi {@code
     * acceptInvite} THẬT phải CHỜ đúng chỗ nó chờ trong sản xuất, rồi mới giải tán party thật (XOÁ
     * dòng {@code party_member} của leader + dòng {@code party}) và nhả khoá — tái hiện ĐÚNG thứ tự
     * "leader rời/giải tán xong TRƯỚC KHI acceptInvite tiếp tục", không phải suy đoán.
     */
    @Test
    @Timeout(15)
    void shouldNotThrow_whenInviteeAcceptsWhileSoleLeaderConcurrentlyDissolvesTheParty() throws Exception {
        long leader = createFreshCharacter("PartyLeader10");
        long invitee = createFreshCharacter("PartyInvitee5");
        PostgresPartyAdapter adapter = newAdapter();
        Party party = adapter.createParty(leader);
        PartyInvite invite = adapter.invite(party.id(), invitee);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch proceedToDissolve = new CountDownLatch(1);
        ExecutorService dissolver = Executors.newSingleThreadExecutor();
        Future<?> dissolveTask = dissolver.submit(() -> {
            holdPartyLockThenDissolve(new PartyRow(party.id(), leader), lockAcquired, proceedToDissolve);
            return null;
        });
        assertThat(lockAcquired.await(5, TimeUnit.SECONDS))
                .as("luồng giải tán phải chiếm được FOR UPDATE trước khi test tiếp tục")
                .isTrue();

        ExecutorService caller = Executors.newSingleThreadExecutor();
        Future<PartyPort.AcceptResult> acceptTask = caller.submit(() -> adapter.acceptInvite(invite.id(), invitee));

        // acceptInvite PHẢI chờ đúng ở khoá dòng party (chưa đọc/ghi gì) trong khi khoá còn bị giữ.
        assertThatThrownBy(() -> acceptTask.get(1, TimeUnit.SECONDS))
                .as("acceptInvite phải CHỜ, không được đọc/ghi ngay trong khi khoá còn bị giữ")
                .isInstanceOf(TimeoutException.class);

        proceedToDissolve.countDown();
        dissolveTask.get(5, TimeUnit.SECONDS);

        // Đây chính là phép đo cho bug: bản CHƯA vá ném DatabaseAccessException (vi phạm FOREIGN KEY)
        // thay vì trả kết quả gọn — client sẽ treo vô thời hạn chờ response không bao giờ tới.
        PartyPort.AcceptResult result = acceptTask.get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo(PartyPort.AcceptResult.NOT_FOUND);
        assertThat(adapter.findPendingInviteOfInvitee(invitee))
                .as("lời mời phải được dọn, không được treo lại mãi trong danh sách của invitee")
                .isEmpty();
        assertThat(adapter.findMembershipOf(invitee)).isEmpty();
    }

    /** Dòng {@code party} và bang chủ duy nhất của nó. */
    private record PartyRow(long partyId, long leaderId) {}

    /**
     * Khoá dòng {@code party} (FOR UPDATE, cùng SQL/thứ tự {@code leaveParty} dùng thật), báo
     * {@code lockAcquired}, GIỮ khoá tới khi được {@code proceedToDissolve}, rồi xoá leader + party và commit.
     */
    private static void holdPartyLockThenDissolve(
            PartyRow party, CountDownLatch lockAcquired, CountDownLatch proceedToDissolve) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            // Cùng SQL/thứ tự leaveParty dùng thật (LOCK_PARTY) — khoá dòng party mà acceptInvite
            // cũng sẽ cố khoá.
            try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM party WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, party.partyId());
                // Dòng phải TỒN TẠI: không có dòng thì FOR UPDATE chẳng khoá gì và phép đo vô nghĩa.
                try (ResultSet locked = lock.executeQuery()) {
                    if (!locked.next()) {
                        throw new IllegalStateException("không có dòng party nào để khoá");
                    }
                }
            }
            lockAcquired.countDown();
            if (!proceedToDissolve.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test không cho phép giải tán kịp — xem lỗi ở luồng chính");
            }
            try (PreparedStatement deleteMember =
                    connection.prepareStatement("DELETE FROM party_member WHERE party_id = ? AND character_id = ?")) {
                deleteMember.setLong(1, party.partyId());
                deleteMember.setLong(2, party.leaderId());
                deleteMember.executeUpdate();
            }
            try (PreparedStatement deleteParty = connection.prepareStatement("DELETE FROM party WHERE id = ?")) {
                deleteParty.setLong(1, party.partyId());
                deleteParty.executeUpdate();
            }
            connection.commit();
        }
    }

    /**
     * Câu dọn lời mời hết hạn (quyết định chủ dự án 2026-10-11) trên Postgres thật: xoá đúng lời mời
     * cũ hơn mốc, để nguyên lời mời còn hạn, và sau đó nhóm khác mời lại được người đó.
     */
    @Test
    void shouldDeleteOnlyInvitesOlderThanTheCutoff() throws Exception {
        long leader = createFreshCharacter("PartyExpLead1");
        long staleInvitee = createFreshCharacter("PartyExpOld1");
        long freshInvitee = createFreshCharacter("PartyExpNew1");
        PostgresPartyAdapter adapter = newAdapter();
        Party party = adapter.createParty(leader);
        PartyInvite stale = adapter.invite(party.id(), staleInvitee);
        adapter.invite(party.id(), freshInvitee);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE party_invite SET invited_at = invited_at - INTERVAL '10 minutes' WHERE id = ?")) {
            statement.setLong(1, stale.id());
            statement.executeUpdate();
        }

        int deleted = adapter.deleteInvitesInvitedBefore(PartyInvite.expiryCutoff(java.time.Instant.now()));

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(adapter.findPendingInviteOfInvitee(staleInvitee)).isEmpty();
        assertThat(adapter.findPendingInviteOfInvitee(freshInvitee)).isPresent();
        assertThat(adapter.acceptInvite(stale.id(), staleInvitee)).isEqualTo(PartyPort.AcceptResult.NOT_FOUND);
        // Lời mời cũ đã đi ⇒ UNIQUE (party_id, invitee) không còn chặn lời mời mới.
        assertThat(adapter.invite(party.id(), staleInvitee).id()).isNotEqualTo(stale.id());
    }

    /**
     * Chuyển quyền trưởng nhóm (quyết định chủ dự án 2026-10-11) trên Postgres thật: hai dòng
     * {@code party_member} và dòng {@code party} đổi cùng nhau, người cũ ở lại nhóm, và trưởng nhóm
     * mới kick được người cũ — tức quyền thật sự đã sang tay.
     */
    @Test
    void shouldTransferLeadershipAtomically_andLetTheNewLeaderKickTheOldOne() {
        long leader = createFreshCharacter("PartyHandLead");
        long heir = createFreshCharacter("PartyHandHeir");
        PostgresPartyAdapter adapter = newAdapter();
        Party party = adapter.createParty(leader);
        adapter.acceptInvite(adapter.invite(party.id(), heir).id(), heir);

        assertThat(adapter.transferLeadership(leader, heir)).isTrue();

        assertThat(adapter.findMembershipOf(heir).orElseThrow().role()).isEqualTo(PartyRole.LEADER);
        assertThat(adapter.findMembershipOf(leader).orElseThrow().role()).isEqualTo(PartyRole.MEMBER);
        assertThat(adapter.listMembers(party.id())).hasSize(2);
        assertThat(adapter.kickMember(leader, heir))
                .as("người cũ không còn quyền kick")
                .isFalse();
        assertThat(adapter.kickMember(heir, leader)).isTrue();
    }

    /** Người nhận không phải thành viên của nhóm đó, hoặc người gọi không phải trưởng nhóm: không đổi gì. */
    @Test
    void shouldRefuseTransfer_whenEitherSideIsNotWhatTheCallerAssumed() {
        long leader = createFreshCharacter("PartyHandLead2");
        long member = createFreshCharacter("PartyHandMem2");
        long outsider = createFreshCharacter("PartyHandOut2");
        PostgresPartyAdapter adapter = newAdapter();
        Party party = adapter.createParty(leader);
        adapter.acceptInvite(adapter.invite(party.id(), member).id(), member);

        assertThat(adapter.transferLeadership(leader, outsider)).isFalse();
        assertThat(adapter.transferLeadership(member, leader)).isFalse();
        assertThat(adapter.transferLeadership(outsider, member)).isFalse();

        assertThat(adapter.findMembershipOf(leader).orElseThrow().role()).isEqualTo(PartyRole.LEADER);
        assertThat(adapter.findMembershipOf(member).orElseThrow().role()).isEqualTo(PartyRole.MEMBER);
    }
}
