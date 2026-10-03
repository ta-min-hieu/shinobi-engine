package dev.kage.server.database.party;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
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
import dev.kage.party.application.AcceptPartyInviteUseCase;
import dev.kage.party.application.CreatePartyUseCase;
import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyInvite;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * MỘT nhân vật lao vào HAI party cùng lúc, đo bằng PostgreSQL THẬT.
 *
 * <p>Dữ liệu vốn an toàn nhờ {@code party_member.character_id UNIQUE} (V15). Câu hỏi ở đây là bên
 * THUA nhận được gì: trước bản vá, transaction thua ném {@code DatabaseAccessException} chung chung,
 * gateway dịch thành {@code error.server_internal} thay vì outcome nghiệp vụ "đã ở trong nhóm" có
 * sẵn trong protocol. Ba đường đua: chấp nhận 2 lời mời của 2 party khác nhau, tạo party trong lúc
 * chấp nhận lời mời, và tạo party 2 lần. Mỗi vòng khẳng định KẾT QUẢ của từng lời gọi lẫn trạng
 * thái DB cuối (không party mồ côi 0 thành viên, không lời mời treo). Cùng khuôn với
 * {@code GuildIntegrationTest#shouldTranslateRaceIntoDuplicateGuildMembershipException...}.
 */
@Testcontainers
class PartyMembershipRaceIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int ROUNDS = 5;
    private static final long CALL_TIMEOUT_SECONDS = 30;
    private static final String OK = "OK";
    private static final String ALREADY_IN_PARTY = "ALREADY_IN_PARTY";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "partymembrace"));
        accountId = accountHolder[0].id();
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void shouldAnswerAlreadyInParty_whenOneCharacterAcceptsInvitesOfTwoPartiesAtOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                PostgresPartyAdapter adapter = new PostgresPartyAdapter(dataSource);
                AcceptPartyInviteUseCase accept = new AcceptPartyInviteUseCase(adapter);
                Party partyA = adapter.createParty(createFreshCharacter("PAcc" + round + "A"));
                Party partyB = adapter.createParty(createFreshCharacter("PAcc" + round + "B"));
                long invitee = createFreshCharacter("PAcc" + round + "X");
                PartyInvite inviteA = adapter.invite(partyA.id(), invitee);
                PartyInvite inviteB = adapter.invite(partyB.id(), invitee);

                List<String> outcomes = race(
                        executor,
                        () -> accept.execute(inviteA.id(), invitee).name(),
                        () -> accept.execute(inviteB.id(), invitee).name());

                assertThat(outcomes)
                        .as("vòng %d: kết quả 2 lời chấp nhận", round)
                        .containsExactlyInAnyOrder(OK, ALREADY_IN_PARTY);
                assertThat(countMembers(partyA.id()) + countMembers(partyB.id()))
                        .as("vòng %d: invitee vào đúng 1 party", round)
                        .isEqualTo(2 + 1);
                assertThat(countPendingInvitesOf(invitee))
                        .as("vòng %d: không lời mời nào còn treo", round)
                        .isZero();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void shouldAnswerAlreadyInParty_whenOneCharacterCreatesAPartyWhileAcceptingAnInvite() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                PostgresPartyAdapter adapter = new PostgresPartyAdapter(dataSource);
                CreatePartyUseCase create = new CreatePartyUseCase(adapter);
                AcceptPartyInviteUseCase accept = new AcceptPartyInviteUseCase(adapter);
                Party existing = adapter.createParty(createFreshCharacter("PMix" + round + "L"));
                long racer = createFreshCharacter("PMix" + round + "X");
                PartyInvite invite = adapter.invite(existing.id(), racer);

                List<String> outcomes = race(
                        executor, () -> create.execute(racer).outcome().name(), () -> accept.execute(invite.id(), racer)
                                .name());

                assertThat(outcomes)
                        .as("vòng %d: [tạo party, chấp nhận lời mời]", round)
                        .containsExactlyInAnyOrder(OK, ALREADY_IN_PARTY);
                assertThat(countPartiesLedBy(racer) + countMembers(existing.id()) - 1)
                        .as("vòng %d: racer ở đúng 1 party (tự lập HOẶC party có sẵn)", round)
                        .isEqualTo(1);
                assertThat(countPendingInvitesOf(racer))
                        .as("vòng %d: lời mời không treo", round)
                        .isZero();
                assertThat(countPartiesWithoutMembers())
                        .as("vòng %d: không party mồ côi", round)
                        .isZero();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void shouldAnswerAlreadyInParty_whenOneCharacterCreatesTwoPartiesAtOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                CreatePartyUseCase create = new CreatePartyUseCase(new PostgresPartyAdapter(dataSource));
                long leader = createFreshCharacter("PDup" + round + "L");

                List<String> outcomes =
                        race(executor, () -> create.execute(leader).outcome().name(), () -> create.execute(leader)
                                .outcome()
                                .name());

                assertThat(outcomes)
                        .as("vòng %d: kết quả 2 lệnh tạo party", round)
                        .containsExactlyInAnyOrder(OK, ALREADY_IN_PARTY);
                assertThat(countPartiesLedBy(leader))
                        .as("vòng %d: đúng 1 party thật", round)
                        .isEqualTo(1);
                assertThat(countPartiesWithoutMembers())
                        .as("vòng %d: không party mồ côi", round)
                        .isZero();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Chạy 2 lời gọi CÙNG LÚC qua {@link CyclicBarrier}. Lời gọi ném exception được ghi thành
     * {@code "THREW:<tên lớp>"} để assert trên kết quả báo đúng loại lỗi thay vì chỉ "test lỗi".
     */
    private static List<String> race(ExecutorService executor, Callable<String> first, Callable<String> second)
            throws InterruptedException, TimeoutException {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<String> firstFuture = executor.submit(() -> {
            barrier.await(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return first.call();
        });
        Future<String> secondFuture = executor.submit(() -> {
            barrier.await(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return second.call();
        });
        return List.of(outcomeOf(firstFuture), outcomeOf(secondFuture));
    }

    private static String outcomeOf(Future<String> future) throws InterruptedException, TimeoutException {
        try {
            return future.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return "THREW:" + e.getCause().getClass().getSimpleName();
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

    private static int countMembers(long partyId) {
        return count("SELECT COUNT(*) FROM party_member WHERE party_id = ?", partyId);
    }

    private static int countPendingInvitesOf(long characterId) {
        return count("SELECT COUNT(*) FROM party_invite WHERE invitee_character_id = ?", characterId);
    }

    private static int countPartiesLedBy(long characterId) {
        return count("SELECT COUNT(*) FROM party WHERE leader_character_id = ?", characterId);
    }

    private static int countPartiesWithoutMembers() {
        return count(
                "SELECT COUNT(*) FROM party p WHERE NOT EXISTS"
                        + " (SELECT 1 FROM party_member m WHERE m.party_id = p.id) AND p.id > ?",
                0L);
    }

    private static int count(String sql, long param) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, param);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm: " + sql, e);
        }
    }
}
