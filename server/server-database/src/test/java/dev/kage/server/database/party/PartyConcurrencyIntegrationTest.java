package dev.kage.server.database.party;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
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
import dev.kage.party.domain.PartyInvite;
import dev.kage.party.domain.PartyPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Trần {@link Party#MAX_MEMBERS} dưới nhiều lần chấp nhận lời mời ĐỒNG THỜI, đo bằng PostgreSQL THẬT.
 *
 * <p>Không có constraint DB nào đếm được số dòng cùng {@code party_id} (V15), nên trần chỉ đứng
 * được nhờ {@code acceptInvite} khoá dòng {@code party} rồi đếm lại TRONG transaction. Lớp này là
 * bằng chứng cho khoá đó (xanh ngay từ đầu) — gỡ khoá thì nó đỏ. Lớp riêng để giữ
 * {@link PartyIntegrationTest} dưới trần độ phức tạp PMD.
 */
@Testcontainers
class PartyConcurrencyIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int EXISTING_MEMBERS = 2;
    private static final int CONCURRENT_ACCEPTS = 6;
    private static final int ROUNDS = 5;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    // volatile: @BeforeAll ghi ở luồng JUnit, test đồng thời đọc từ luồng khác (SpotBugs AT).
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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "partyracer"));
        accountId = accountHolder[0].id();
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void shouldNeverExceedMaxMembers_whenManyInvitesAreAcceptedAtOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ACCEPTS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                PostgresPartyAdapter adapter = new PostgresPartyAdapter(dataSource);
                long leader = createFreshCharacter("PCap" + round + "L");
                Party party = adapter.createParty(leader);
                long member = createFreshCharacter("PCap" + round + "M");
                PartyInvite memberInvite = adapter.invite(party.id(), member);
                assertThat(adapter.acceptInvite(memberInvite.id(), member)).isEqualTo(PartyPort.AcceptResult.OK);

                List<PartyPort.AcceptResult> results = acceptConcurrently(executor, adapter, party, round);

                assertThat(countMembers(party.id()))
                        .as("vòng %d: số thành viên cuối", round)
                        .isEqualTo(Party.MAX_MEMBERS);
                assertThat(results)
                        .filteredOn(result -> result == PartyPort.AcceptResult.OK)
                        .hasSize(Party.MAX_MEMBERS - EXISTING_MEMBERS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<PartyPort.AcceptResult> acceptConcurrently(
            ExecutorService executor, PostgresPartyAdapter adapter, Party party, int round)
            throws InterruptedException, ExecutionException {
        CyclicBarrier barrier = new CyclicBarrier(CONCURRENT_ACCEPTS);
        List<Future<PartyPort.AcceptResult>> futures = new ArrayList<Future<PartyPort.AcceptResult>>();
        for (int i = 0; i < CONCURRENT_ACCEPTS; i++) {
            long invitee = createFreshCharacter("PCap" + round + "I" + i);
            PartyInvite invite = adapter.invite(party.id(), invitee);
            futures.add(executor.submit(() -> {
                barrier.await(30, TimeUnit.SECONDS);
                return adapter.acceptInvite(invite.id(), invitee);
            }));
        }
        List<PartyPort.AcceptResult> results = new ArrayList<PartyPort.AcceptResult>();
        for (Future<PartyPort.AcceptResult> future : futures) {
            results.add(future.get());
        }
        return results;
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

    private static int countMembers(long partyId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT COUNT(*) FROM party_member WHERE party_id = ?")) {
            statement.setLong(1, partyId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm party_member: partyId=" + partyId, e);
        }
    }
}
