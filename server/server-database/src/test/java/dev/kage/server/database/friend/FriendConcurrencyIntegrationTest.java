package dev.kage.server.database.friend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import dev.kage.friend.api.FriendFacade;
import dev.kage.friend.application.AcceptFriendRequestUseCase;
import dev.kage.friend.application.FriendUseCases;
import dev.kage.friend.application.ListFriendsUseCase;
import dev.kage.friend.application.ListPendingFriendRequestsUseCase;
import dev.kage.friend.application.RejectFriendRequestUseCase;
import dev.kage.friend.application.RemoveFriendUseCase;
import dev.kage.friend.application.SendFriendRequestUseCase;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * {@link FriendFacade} dưới lệnh ĐỒNG THỜI, đo bằng PostgreSQL THẬT (rà 2026-10-10).
 *
 * <p>Mọi phép đo ở đây là GIÁ TRỊ CUỐI trong bảng {@code friendship}, không phải "có ném lỗi
 * không": số dòng của một cặp, trạng thái của dòng đó, và sổ cái "số lệnh báo OK phải khớp số dòng
 * còn lại". Đi qua facade thật (use case + adapter) chứ không gọi thẳng adapter — phép kiểm
 * {@code findBetween} ngoài giao dịch của use case là một nửa của cửa sổ race.
 *
 * <p>Lớp này xanh ngay từ lần chạy đầu: khoá tư vấn theo cặp của {@code sendRequest} và các câu
 * {@code UPDATE}/{@code DELETE} có điều kiện đứng vững. Gỡ khoá đó thì
 * {@link #shouldKeepAtMostOneRowPerPair_underRandomStorm} đỏ.
 */
@Testcontainers
class FriendConcurrencyIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int ROUNDS = 25;
    private static final int STORM_THREADS = 8;
    private static final int STORM_CHARACTERS = 4;
    private static final int STORM_OPERATIONS_PER_THREAD = 150;
    private static final int OPERATION_KINDS = 4;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "friendracer"));
        accountId = accountHolder[0].id();
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void shouldLetExactlyOneWin_whenAcceptAndRejectHitTheSameRequest() throws Exception {
        FriendFacade facade = newFacade();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long requester = createFreshCharacter("FcArA" + round);
                long addressee = createFreshCharacter("FcArB" + round);
                long friendshipId = sendAndFindRequestId(facade, requester, addressee);

                List<Boolean> won = runTogether(
                        executor,
                        () -> facade.accept(friendshipId, addressee) == FriendFacade.AcceptResult.OK,
                        () -> facade.reject(friendshipId, addressee) == FriendFacade.RejectResult.OK);

                boolean accepted = won.get(0);
                boolean rejected = won.get(1);
                assertThat(accepted ^ rejected)
                        .as("vòng %d: đúng một bên thắng", round)
                        .isTrue();
                assertThat(countRows(requester, addressee, "ACCEPTED"))
                        .as("vòng %d: dòng ACCEPTED cuối", round)
                        .isEqualTo(accepted ? 1 : 0);
                assertThat(countRows(requester, addressee, "PENDING"))
                        .as("vòng %d: không còn lời mời treo", round)
                        .isZero();
                assertThat(facade.listFriends(requester)).hasSize(accepted ? 1 : 0);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void shouldAcceptOnce_andRemoveOnce_whenBothCommandsAreDoubled() throws Exception {
        FriendFacade facade = newFacade();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long requester = createFreshCharacter("FcDbA" + round);
                long addressee = createFreshCharacter("FcDbB" + round);
                long friendshipId = sendAndFindRequestId(facade, requester, addressee);

                List<Boolean> accepts = runTogether(
                        executor,
                        () -> facade.accept(friendshipId, addressee) == FriendFacade.AcceptResult.OK,
                        () -> facade.accept(friendshipId, addressee) == FriendFacade.AcceptResult.OK);

                assertThat(accepts).as("vòng %d: chấp nhận hai lần", round).containsExactlyInAnyOrder(true, false);
                assertThat(facade.listFriends(requester)).containsExactly(addressee);
                assertThat(facade.listFriends(addressee)).containsExactly(requester);

                // Hai phía cùng huỷ kết bạn với nhau đúng một lúc.
                List<Boolean> removes = runTogether(
                        executor,
                        () -> facade.remove(requester, addressee) == FriendFacade.RemoveResult.OK,
                        () -> facade.remove(addressee, requester) == FriendFacade.RemoveResult.OK);

                assertThat(removes).as("vòng %d: huỷ hai phía", round).containsExactlyInAnyOrder(true, false);
                assertThat(countRows(requester, addressee, "ACCEPTED") + countRows(requester, addressee, "PENDING"))
                        .as("vòng %d: không còn dòng nào", round)
                        .isZero();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // Huỷ kết bạn đúng lúc phía kia gửi lại lời mời: không được sinh dòng thứ hai, và lời mời mới
    // (nếu lọt) phải là PENDING chứ không được "thừa hưởng" trạng thái bạn bè vừa bị huỷ.
    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void shouldEndWithAtMostOnePendingRow_whenRemoveRacesANewRequest() throws Exception {
        FriendFacade facade = newFacade();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long a = createFreshCharacter("FcRmA" + round);
                long b = createFreshCharacter("FcRmB" + round);
                long friendshipId = sendAndFindRequestId(facade, a, b);
                assertThat(facade.accept(friendshipId, b)).isEqualTo(FriendFacade.AcceptResult.OK);

                List<Boolean> results = runTogether(
                        executor,
                        () -> facade.remove(a, b) == FriendFacade.RemoveResult.OK,
                        () -> facade.sendRequest(b, a) == FriendFacade.SendRequestResult.OK);

                boolean resent = results.get(1);
                assertThat(results.get(0))
                        .as("vòng %d: huỷ kết bạn luôn thành", round)
                        .isTrue();
                assertThat(countRows(a, b, "ACCEPTED")).as("vòng %d", round).isZero();
                assertThat(countRows(a, b, "PENDING")).as("vòng %d", round).isEqualTo(resent ? 1 : 0);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Bão lệnh ngẫu nhiên trên một nhóm nhỏ nhân vật — nhiều luồng chen nhau trên CÙNG vài cặp, mọi
     * thứ tự gửi/chấp nhận/từ chối/huỷ. Hạt giống cố định để tái lập khi cần điều tra.
     *
     * <p>Bất biến cuối: mỗi cặp nhiều nhất MỘT dòng; không dòng tự-kết-bạn; và sổ cái khớp —
     * {@code gửi OK − từ chối OK − huỷ OK = số dòng còn lại},
     * {@code chấp nhận OK − huỷ OK = số dòng ACCEPTED}. Một lệnh báo OK mà không để lại dấu trong
     * bảng (hoặc ngược lại) sẽ làm lệch sổ. Deadlock/lỗi DB hiện ra thành exception của luồng.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void shouldKeepAtMostOneRowPerPair_underRandomStorm() throws Exception {
        FriendFacade facade = newFacade();
        long[] characters = new long[STORM_CHARACTERS];
        for (int i = 0; i < STORM_CHARACTERS; i++) {
            characters[i] = createFreshCharacter("FcStorm" + i);
        }
        StormLedger ledger = new StormLedger();
        ExecutorService executor = Executors.newFixedThreadPool(STORM_THREADS);
        try {
            CyclicBarrier barrier = new CyclicBarrier(STORM_THREADS);
            List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
            for (int thread = 0; thread < STORM_THREADS; thread++) {
                SplittableRandom random = new SplittableRandom(20261010L + thread);
                futures.add(executor.submit(() -> {
                    barrier.await(30, TimeUnit.SECONDS);
                    for (int i = 0; i < STORM_OPERATIONS_PER_THREAD; i++) {
                        runRandomOperation(facade, characters, random, ledger);
                    }
                    return Boolean.TRUE;
                }));
            }
            for (Future<Boolean> future : futures) {
                assertThat(future.get(4, TimeUnit.MINUTES)).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }

        long first = characters[0];
        long last = characters[STORM_CHARACTERS - 1];
        assertThat(countLong("""
                        SELECT COUNT(*) FROM (
                            SELECT LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id)
                              FROM friendship
                             WHERE requester_id BETWEEN ? AND ? AND addressee_id BETWEEN ? AND ?
                             GROUP BY 1, 2 HAVING COUNT(*) > 1) AS duplicated
                        """, first, last, first, last))
                .as("số cặp có hơn một dòng")
                .isZero();
        long totalRows = countLong(
                "SELECT COUNT(*) FROM friendship WHERE requester_id BETWEEN ? AND ? AND addressee_id BETWEEN ? AND ?",
                first,
                last,
                first,
                last);
        long acceptedRows = countLong("""
                        SELECT COUNT(*) FROM friendship
                         WHERE status = 'ACCEPTED' AND responded_at IS NOT NULL
                           AND requester_id BETWEEN ? AND ? AND addressee_id BETWEEN ? AND ?
                        """, first, last, first, last);
        assertThat(ledger.sent.get()).as("bão phải thật sự tạo ra lời mời").isPositive();
        assertThat(ledger.accepted.get())
                .as("bão phải thật sự chấp nhận lời mời")
                .isPositive();
        assertThat(totalRows)
                .as("gửi OK − từ chối OK − huỷ OK")
                .isEqualTo(ledger.sent.get() - ledger.rejected.get() - ledger.removed.get());
        assertThat(acceptedRows).as("chấp nhận OK − huỷ OK").isEqualTo(ledger.accepted.get() - ledger.removed.get());
    }

    private static void runRandomOperation(
            FriendFacade facade, long[] characters, SplittableRandom random, StormLedger ledger) {
        long actor = characters[random.nextInt(characters.length)];
        long other = characters[random.nextInt(characters.length)];
        switch (random.nextInt(OPERATION_KINDS)) {
            case 0 -> ledger.sent.addAndGet(send(facade, actor, other));
            case 1 -> ledger.accepted.addAndGet(acceptAllPending(facade, actor));
            case 2 -> ledger.rejected.addAndGet(rejectAllPending(facade, actor));
            default -> ledger.removed.addAndGet(removeFriend(facade, actor, other));
        }
    }

    private static int send(FriendFacade facade, long actor, long other) {
        return facade.sendRequest(actor, other) == FriendFacade.SendRequestResult.OK ? 1 : 0;
    }

    private static int acceptAllPending(FriendFacade facade, long actor) {
        int accepted = 0;
        for (FriendFacade.PendingRequestView pending : facade.listPendingRequests(actor)) {
            if (facade.accept(pending.friendshipId(), actor) == FriendFacade.AcceptResult.OK) {
                accepted++;
            }
        }
        return accepted;
    }

    private static int rejectAllPending(FriendFacade facade, long actor) {
        int rejected = 0;
        for (FriendFacade.PendingRequestView pending : facade.listPendingRequests(actor)) {
            if (facade.reject(pending.friendshipId(), actor) == FriendFacade.RejectResult.OK) {
                rejected++;
            }
        }
        return rejected;
    }

    private static int removeFriend(FriendFacade facade, long actor, long other) {
        return actor != other && facade.remove(actor, other) == FriendFacade.RemoveResult.OK ? 1 : 0;
    }

    /** Số lệnh đã báo OK, theo loại — đối chiếu với bảng ở cuối bão. */
    private static final class StormLedger {
        private final AtomicInteger sent = new AtomicInteger();
        private final AtomicInteger accepted = new AtomicInteger();
        private final AtomicInteger rejected = new AtomicInteger();
        private final AtomicInteger removed = new AtomicInteger();
    }

    private static List<Boolean> runTogether(
            ExecutorService executor, Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<Boolean> firstResult = executor.submit(() -> {
            barrier.await(30, TimeUnit.SECONDS);
            return first.call();
        });
        Future<Boolean> secondResult = executor.submit(() -> {
            barrier.await(30, TimeUnit.SECONDS);
            return second.call();
        });
        return List.of(firstResult.get(1, TimeUnit.MINUTES), secondResult.get(1, TimeUnit.MINUTES));
    }

    private static long sendAndFindRequestId(FriendFacade facade, long requester, long addressee) {
        assertThat(facade.sendRequest(requester, addressee)).isEqualTo(FriendFacade.SendRequestResult.OK);
        List<FriendFacade.PendingRequestView> pending = facade.listPendingRequests(addressee);
        assertThat(pending).hasSize(1);
        return pending.getFirst().friendshipId();
    }

    private static long countRows(long characterIdA, long characterIdB, String status) {
        return countLong("""
                        SELECT COUNT(*) FROM friendship
                         WHERE status = ?
                           AND ((requester_id = ? AND addressee_id = ?) OR (requester_id = ? AND addressee_id = ?))
                        """, status, characterIdA, characterIdB, characterIdB, characterIdA);
    }

    private static long countLong(String sql, Object... params) {
        return JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), params)
                .orElseThrow();
    }

    private static FriendFacade newFacade() {
        PostgresFriendAdapter adapter = new PostgresFriendAdapter(dataSource);
        return new FriendFacade(new FriendUseCases(
                new SendFriendRequestUseCase(adapter),
                new AcceptFriendRequestUseCase(adapter),
                new RejectFriendRequestUseCase(adapter),
                new RemoveFriendUseCase(adapter),
                new ListFriendsUseCase(adapter),
                new ListPendingFriendRequestsUseCase(adapter)));
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
