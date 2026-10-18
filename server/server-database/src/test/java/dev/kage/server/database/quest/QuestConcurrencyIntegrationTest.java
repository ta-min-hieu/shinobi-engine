package dev.kage.server.database.quest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ProcessQuestEventUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.quest.domain.QuestLogEntry;
import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Race "đọc trạng thái rồi mới ghi" của nhận/lĩnh quest, đo bằng PostgreSQL THẬT.
 *
 * <p>Hai lệnh của CÙNG một kết nối chạy SONG SONG ở gateway (mỗi lệnh chạm DB là một Virtual
 * Thread riêng trên {@code authExecutor}), nên bấm đúp đã đủ để tạo ra hai lần gọi chồng nhau —
 * không cần client độc hại. Mọi phép kiểm ở đây đọc GIÁ TRỊ CUỐI trong DB (EXP, số vật phẩm,
 * status), không đọc "có bị chặn không".
 */
@Testcontainers
class QuestConcurrencyIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final String QUEST_CODE = "kill_training_dummies";
    private static final String QUEST_MONSTER = "training_dummy";
    private static final int KILLS_TO_COMPLETE = 3;
    private static final long QUEST_EXP_REWARD = 500L;
    private static final String QUEST_ITEM_REWARD = "kunai";
    private static final int QUEST_ITEM_REWARD_QUANTITY = 5;
    private static final int CONCURRENT_CLAIMS = 8;
    private static final int ROUNDS = 5;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "questracer"));
        accountId = accountHolder[0].id();
    }

    /**
     * Lĩnh thưởng: {@code idempotency_key} chỉ chặn lần gửi LẠI của CÙNG một lệnh. Hai lệnh lĩnh
     * khác nhau (mỗi lần bấm là một key mới) cùng đọc thấy COMPLETED rồi cùng trao thưởng — bản
     * cũ trao EXP + vật phẩm nhiều lần cho một quest.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void shouldGrantRewardExactlyOnce_whenManyClaimsWithDifferentKeysRace() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CLAIMS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long characterId = createFreshCharacter("QRaceClaim" + round);
                completeQuest(characterId);
                long expBefore = readExp(characterId);
                int itemsBefore = countItemQuantity(characterId, QUEST_ITEM_REWARD);

                List<ClaimQuestRewardUseCase.ClaimResult.Outcome> outcomes = claimConcurrently(executor, characterId);

                assertThat(readExp(characterId) - expBefore)
                        .as("EXP trao ở vòng %d", round)
                        .isEqualTo(QUEST_EXP_REWARD);
                assertThat(countItemQuantity(characterId, QUEST_ITEM_REWARD) - itemsBefore)
                        .as("số %s trao ở vòng %d", QUEST_ITEM_REWARD, round)
                        .isEqualTo(QUEST_ITEM_REWARD_QUANTITY);
                assertThat(outcomes)
                        .filteredOn(outcome -> outcome == ClaimQuestRewardUseCase.ClaimResult.Outcome.OK)
                        .hasSize(1);
                assertThat(readStatus(characterId)).contains(QuestStatus.CLAIMED.name());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<ClaimQuestRewardUseCase.ClaimResult.Outcome> claimConcurrently(
            ExecutorService executor, long characterId) throws InterruptedException, ExecutionException {
        CyclicBarrier barrier = new CyclicBarrier(CONCURRENT_CLAIMS);
        List<Future<ClaimQuestRewardUseCase.ClaimResult.Outcome>> futures =
                new ArrayList<Future<ClaimQuestRewardUseCase.ClaimResult.Outcome>>();
        for (int i = 0; i < CONCURRENT_CLAIMS; i++) {
            futures.add(executor.submit(() -> {
                ClaimQuestRewardUseCase useCase = newClaimUseCase();
                String key = UUID.randomUUID().toString();
                barrier.await(30, TimeUnit.SECONDS);
                return useCase.execute(characterId, QUEST_CODE, key).outcome();
            }));
        }
        List<ClaimQuestRewardUseCase.ClaimResult.Outcome> outcomes =
                new ArrayList<ClaimQuestRewardUseCase.ClaimResult.Outcome>();
        for (Future<ClaimQuestRewardUseCase.ClaimResult.Outcome> future : futures) {
            outcomes.add(future.get());
        }
        return outcomes;
    }

    /**
     * Nhận quest: lần nhận thứ hai đọc "chưa có dòng" TRƯỚC khi lần thứ nhất commit, rồi ghi SAU
     * khi người chơi đã làm xong và lĩnh thưởng. Bản cũ ghi bằng upsert mù nên kéo quest đã
     * CLAIMED về IN_PROGRESS với tiến độ số 0 — làm lại được, lĩnh thưởng lại được.
     *
     * <p>Khoảng hở được giữ mở bằng {@link GatedProgressPort} để thứ tự xen kẽ là tất định (không
     * trông vào may rủi của bộ lập lịch): lần nhận "chậm" dừng ngay trước lần ghi của nó.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void shouldNotResetAClaimedQuest_whenALateDuplicateAcceptLands() throws Exception {
        long characterId = createFreshCharacter("QRaceAccept");
        CountDownLatch slowAcceptReachedWrite = new CountDownLatch(1);
        CountDownLatch releaseSlowAccept = new CountDownLatch(1);
        GatedProgressPort gatedPort = new GatedProgressPort(
                new PostgresQuestProgressAdapter(dataSource), slowAcceptReachedWrite, releaseSlowAccept);
        AcceptQuestUseCase slowAccept = new AcceptQuestUseCase(
                gatedPort, new PostgresQuestTemplateAdapter(dataSource), new JdbcQuestTransactionRunner(dataSource));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AcceptQuestUseCase.AcceptResult> late =
                    executor.submit(() -> slowAccept.execute(characterId, QUEST_CODE));
            assertThat(slowAcceptReachedWrite.await(60, TimeUnit.SECONDS)).isTrue();

            completeQuest(characterId);
            assertThat(newClaimUseCase()
                            .execute(characterId, QUEST_CODE, UUID.randomUUID().toString())
                            .outcome())
                    .isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);

            releaseSlowAccept.countDown();
            AcceptQuestUseCase.AcceptResult lateResult = late.get(60, TimeUnit.SECONDS);

            assertThat(readStatus(characterId)).contains(QuestStatus.CLAIMED.name());
            assertThat(lateResult).isEqualTo(AcceptQuestUseCase.AcceptResult.ALREADY_ACCEPTED);
        } finally {
            releaseSlowAccept.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * Bọc adapter thật; lần GHI đầu tiên dừng lại chờ test mở cửa. Mọi thứ khác đi thẳng xuống
     * Postgres.
     */
    private static final class GatedProgressPort implements QuestProgressPort {

        private final QuestProgressPort delegate;
        private final CountDownLatch reachedWrite;
        private final CountDownLatch release;

        GatedProgressPort(QuestProgressPort delegate, CountDownLatch reachedWrite, CountDownLatch release) {
            this.delegate = delegate;
            this.reachedWrite = reachedWrite;
            this.release = release;
        }

        private void awaitGate() {
            reachedWrite.countDown();
            try {
                if (!release.await(60, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test không mở cửa cho lần nhận chậm");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("bị ngắt khi chờ cửa", e);
            }
        }

        @Override
        public Optional<QuestProgress> find(long characterId, long questTemplateId) {
            return delegate.find(characterId, questTemplateId);
        }

        @Override
        public void save(Transaction tx, QuestProgress progress) {
            awaitGate();
            delegate.save(tx, progress);
        }

        @Override
        public boolean insertIfAbsent(Transaction tx, QuestProgress progress) {
            awaitGate();
            return delegate.insertIfAbsent(tx, progress);
        }

        @Override
        public List<QuestLogEntry> listLog(long characterId) {
            return delegate.listLog(characterId);
        }
    }

    private static void completeQuest(long characterId) {
        newAcceptUseCase().execute(characterId, QUEST_CODE);
        for (int i = 0; i < KILLS_TO_COMPLETE; i++) {
            newProcessUseCase().monsterKilled(characterId, QUEST_MONSTER);
        }
        assertThat(readStatus(characterId)).contains(QuestStatus.COMPLETED.name());
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

    private static AcceptQuestUseCase newAcceptUseCase() {
        return new AcceptQuestUseCase(
                new PostgresQuestProgressAdapter(dataSource),
                new PostgresQuestTemplateAdapter(dataSource),
                new JdbcQuestTransactionRunner(dataSource));
    }

    private static ProcessQuestEventUseCase newProcessUseCase() {
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        return new ProcessQuestEventUseCase(
                new PostgresQuestProgressAdapter(dataSource),
                new PostgresQuestItemTakeAdapter(),
                new PostgresQuestAuditAdapter(),
                tx);
    }

    private static ClaimQuestRewardUseCase newClaimUseCase() {
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(),
                new PostgresQuestInventoryGrantAdapter(),
                new PostgresQuestAuditAdapter(),
                new PostgresQuestIdempotencyAdapter(),
                new PostgresQuestGoldAdapter());
        return new ClaimQuestRewardUseCase(
                new PostgresQuestProgressAdapter(dataSource),
                new PostgresQuestTemplateAdapter(dataSource),
                rewardPorts,
                new JdbcQuestTransactionRunner(dataSource));
    }

    private static Optional<String> readStatus(long characterId) {
        String sql = "SELECT cqp.status FROM character_quest_progress cqp"
                + " JOIN quest_template qt ON qt.id = cqp.quest_template_id"
                + " WHERE cqp.character_id = ? AND qt.code = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, characterId);
            statement.setString(2, QUEST_CODE);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc status quest: characterId=" + characterId, e);
        }
    }

    private static long readExp(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT exp FROM character WHERE id = ?")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc exp: characterId=" + characterId, e);
        }
    }

    private static int countItemQuantity(long characterId, String itemCode) {
        String sql = "SELECT COALESCE(SUM(ii.quantity), 0) FROM inventory_item ii"
                + " JOIN item_template it ON it.id = ii.item_template_id"
                + " WHERE ii.character_id = ? AND it.code = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, characterId);
            statement.setString(2, itemCode);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm vật phẩm: " + itemCode, e);
        }
    }
}
