package dev.kage.server.database.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyDissolvedException;
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
 * Rời nhóm / giải tán nhóm chạy CHỒNG lên thao tác khác trên cùng party, đo bằng PostgreSQL THẬT.
 * Lớp riêng để giữ {@link PartyIntegrationTest} dưới trần độ phức tạp PMD.
 */
@Testcontainers
class PartyDissolveRaceIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final long WAIT_POLL_MILLIS = 20;
    private static final long WAIT_LIMIT_MILLIS = 5_000;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác (SpotBugs AT).
    static volatile long accountId;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "partydissolver"));
        accountId = accountHolder[0].id();
    }

    /**
     * ⚠️ Deadlock THẬT (2026-10-03): leader rời nhóm đúng lúc một thành viên cũng rời. Bản cũ khoá
     * dòng {@code party_member} của CHÍNH người rời trước, rồi mới khoá dòng {@code party}; leader
     * còn khoá thêm dòng {@code party_member} của thành viên vào sớm nhất để thăng chức. Thành viên B
     * giữ dòng của B và chờ dòng {@code party}; leader giữ dòng {@code party} và chờ dòng của B ⇒
     * Postgres huỷ một bên bằng "deadlock detected" ⇒ client bấm "Rời nhóm" nhận 500.
     *
     * <p>Thứ tự được ÉP, không phụ thuộc may rủi: một kết nối riêng giữ khoá dòng {@code party}, cho
     * leader xếp hàng chờ trước, rồi tới B, rồi mới nhả.
     */
    @Test
    @Timeout(30)
    void shouldLetLeaderAndMemberLeaveAtOnce_withoutDeadlock_andPromoteTheOneWhoStays() throws Exception {
        long leader = createFreshCharacter("PDisL1");
        long earliest = createFreshCharacter("PDisB1");
        long stays = createFreshCharacter("PDisC1");
        PostgresPartyAdapter adapter = new PostgresPartyAdapter(dataSource);
        Party party = adapter.createParty(leader);
        assertThat(adapter.acceptInvite(adapter.invite(party.id(), earliest).id(), earliest))
                .isEqualTo(PartyPort.AcceptResult.OK);
        assertThat(adapter.acceptInvite(adapter.invite(party.id(), stays).id(), stays))
                .isEqualTo(PartyPort.AcceptResult.OK);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            lockPartyRow(holder, party.id());

            Future<PartyPort.LeaveResult> leaderLeaves = executor.submit(() -> adapter.leaveParty(leader));
            awaitLockWaiters(1);
            Future<PartyPort.LeaveResult> memberLeaves = executor.submit(() -> adapter.leaveParty(earliest));
            awaitLockWaiters(2);

            holder.commit();

            assertThat(leaderLeaves.get(15, TimeUnit.SECONDS)).isEqualTo(PartyPort.LeaveResult.OK);
            assertThat(memberLeaves.get(15, TimeUnit.SECONDS)).isEqualTo(PartyPort.LeaveResult.OK);
        } finally {
            executor.shutdownNow();
        }

        assertThat(adapter.findMembershipOf(leader)).isEmpty();
        assertThat(adapter.findMembershipOf(earliest)).isEmpty();
        assertThat(adapter.listMembers(party.id()))
                .as("người duy nhất còn lại phải là leader")
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.characterId()).isEqualTo(stays);
                    assertThat(member.role()).isEqualTo(PartyRole.LEADER);
                });
        assertThat(partyLeaderOf(party.id())).isEqualTo(stays);
    }

    /**
     * Leader mời người khác đúng lúc party vừa bị giải tán (leader cuối rời ở kết nối khác): use case
     * đã qua bước "là leader" rồi mới ghi. Bản cũ để vi phạm FOREIGN KEY {@code party_id} thành
     * {@code DatabaseAccessException} chung ⇒ 500 {@code error.server_internal}.
     */
    @Test
    void shouldThrowPartyDissolved_whenInvitingIntoAPartyThatWasJustDissolved() {
        long leader = createFreshCharacter("PDisL2");
        long invitee = createFreshCharacter("PDisI2");
        PostgresPartyAdapter adapter = new PostgresPartyAdapter(dataSource);
        Party party = adapter.createParty(leader);
        assertThat(adapter.leaveParty(leader)).isEqualTo(PartyPort.LeaveResult.OK);

        assertThatThrownBy(() -> adapter.invite(party.id(), invitee)).isInstanceOf(PartyDissolvedException.class);
        assertThat(adapter.findPendingInviteOfInvitee(invitee)).isEmpty();
    }

    private static void lockPartyRow(Connection connection, long partyId) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM party WHERE id = ? FOR UPDATE")) {
            lock.setLong(1, partyId);
            try (ResultSet rs = lock.executeQuery()) {
                assertThat(rs.next()).isTrue();
            }
        }
    }

    // Đợi tới khi đúng N giao dịch khác đang CHỜ KHOÁ — mốc thứ tự thật, không phải sleep đoán mò.
    private static void awaitLockWaiters(int expected) throws SQLException, InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_LIMIT_MILLIS;
        while (countLockWaiters() < expected) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("không thấy " + expected + " giao dịch chờ khoá");
            }
            Thread.sleep(WAIT_POLL_MILLIS);
        }
    }

    private static int countLockWaiters() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                                + " AND datname = current_database()");
                ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static long partyLeaderOf(long partyId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT leader_character_id FROM party WHERE id = ?")) {
            statement.setLong(1, partyId);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).as("party phải còn tồn tại").isTrue();
                return rs.getLong(1);
            }
        }
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
