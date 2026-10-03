package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
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
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EnhancePorts;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.trade.JdbcTradeTransactionRunner;
import dev.kage.server.database.trade.PostgresTradeAuditAdapter;
import dev.kage.server.database.trade.PostgresTradeGoldAdapter;
import dev.kage.server.database.trade.PostgresTradeIdempotencyAdapter;
import dev.kage.server.database.trade.PostgresTradeInventoryAdapter;
import dev.kage.server.database.trade.PostgresTradeSessionAdapter;
import dev.kage.trade.application.AcceptTradeRequestUseCase;
import dev.kage.trade.application.CancelTradeUseCase;
import dev.kage.trade.application.ConfirmTradeUseCase;
import dev.kage.trade.application.RequestTradeUseCase;
import dev.kage.trade.application.SetTradeOfferUseCase;
import dev.kage.trade.application.TradePorts;
import dev.kage.trade.domain.OfferedItemInput;

/**
 * Món đang nằm trong lời chào của một phiên giao dịch còn mở KHÔNG được cường hoá (quyết định chủ
 * dự án 2026-09-29).
 *
 * <p><b>Lỗi thật.</b> {@code trade_offer_item} không lưu {@code enhance_level}: người nhận xác nhận
 * khi thấy thanh kiếm +0, người đưa cường hoá lên +1 rồi mới xác nhận — giao dịch thi hành với một
 * món KHÁC giá trị với thứ đã được đồng ý. Hướng ngược (cường hoá xong rồi mới chào) thì hợp lệ: lời
 * chào hiện trạng thái hiện tại.
 *
 * <p>Đo GIÁ TRỊ CUỐI (bài học PvP rating race): cấp cường hoá, vàng, cấp món người nhận thật sự nhận
 * được so với cấp họ nhìn thấy lúc xác nhận.
 */
@Testcontainers
class EnhanceWhileInTradeIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final long START_GOLD = 10_000L;
    private static final int RACE_ROUNDS = 12;
    private static final long TASK_TIMEOUT_SECONDS = 30L;
    private static final String SET_GOLD = "UPDATE character_stat SET gold = ? WHERE character_id = ?";
    private static final String INSERT_ITEM =
            "INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)"
                    + " VALUES (?, ?, 1, 0, 0) RETURNING id";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static volatile long accountId;
    static volatile long woodenSwordTemplateId;
    static ExecutorService executor;
    static int nameCounter;

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
        Account[] accountHolder = new Account[1];
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "enhancetradetester"));
        accountId = accountHolder[0].id();
        woodenSwordTemplateId = queryLong("SELECT id FROM item_template WHERE code = 'wooden_sword'");
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterAll
    static void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Đúng kịch bản lỗi: chào kiếm, bên kia đã xác nhận, người đưa bấm cường hoá ⇒ bị từ chối, vàng
     * và cấp không đổi; xác nhận nốt thì người nhận được ĐÚNG thanh +0 đã thấy.
     */
    @Test
    void shouldRejectEnhance_whenItemIsOfferedAndTheOtherSideConfirmed() {
        long giverId = newCharacter();
        long receiverId = newCharacter();
        long swordId = giveSword(giverId);
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        long sessionId = openSession(sessionPort, giverId, receiverId);
        new SetTradeOfferUseCase(sessionPort).execute(sessionId, giverId, 0, List.of(new OfferedItemInput(swordId, 1)));
        ConfirmTradeUseCase confirm = newConfirmTradeUseCase(sessionPort);
        assertThat(confirm.execute(sessionId, receiverId, newKey())).isEqualTo(ConfirmTradeUseCase.Result.OK);

        EnhanceItemUseCase.EnhanceResult result = newEnhanceUseCase().execute(giverId, swordId, false, newKey());

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.ITEM_IN_TRADE);
        assertThat(enhanceLevel(swordId)).as("cấp cường hoá").isZero();
        assertThat(readGold(giverId)).as("vàng người đưa").isEqualTo(START_GOLD);
        assertThat(confirm.execute(sessionId, giverId, newKey())).isEqualTo(ConfirmTradeUseCase.Result.EXECUTED);
        assertThat(ownerOf(swordId)).isEqualTo(receiverId);
        assertThat(enhanceLevel(swordId))
                .as("người nhận được đúng thanh +0 đã thấy")
                .isZero();
    }

    /** Phiên đã huỷ ⇒ lời chào không còn ⇒ cường hoá lại được như thường. */
    @Test
    void shouldAllowEnhance_afterTheTradeIsCancelled() {
        long giverId = newCharacter();
        long receiverId = newCharacter();
        long swordId = giveSword(giverId);
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        long sessionId = openSession(sessionPort, giverId, receiverId);
        new SetTradeOfferUseCase(sessionPort).execute(sessionId, giverId, 0, List.of(new OfferedItemInput(swordId, 1)));
        new CancelTradeUseCase(sessionPort).execute(sessionId, receiverId);

        EnhanceItemUseCase.EnhanceResult result = newEnhanceUseCase().execute(giverId, swordId, false, newKey());

        assertThat(result)
                .isIn(EnhanceItemUseCase.EnhanceResult.SUCCESS, EnhanceItemUseCase.EnhanceResult.FAILED_NO_DROP);
        assertThat(readGold(giverId)).isLessThan(START_GOLD);
    }

    /**
     * Cường hoá và đặt lời chào bắn CÙNG LÚC nhiều vòng. Bên đặt lời chào đọc cấp cường hoá ĐÃ COMMIT
     * ngay sau khi đặt xong (đúng thứ client của người nhận thấy), rồi hai bên xác nhận. Bất biến:
     * giao dịch thi hành ⇒ người nhận nhận đúng cấp đã thấy; cường hoá bị từ chối ⇒ vàng/cấp nguyên
     * vẹn. Không có điểm tuần tự hoá chung, cường hoá kiểm "chưa chào" rồi commit SAU khi lời chào
     * đã lộ ra ⇒ người nhận thấy +0 mà nhận +1.
     */
    @Test
    void shouldDeliverExactlyTheLevelTheReceiverSaw_whenEnhanceAndOfferRace() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            long giverId = newCharacter();
            long receiverId = newCharacter();
            long swordId = giveSword(giverId);
            PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
            long sessionId = openSession(sessionPort, giverId, receiverId);
            CyclicBarrier barrier = new CyclicBarrier(2);

            Future<EnhanceItemUseCase.EnhanceResult> enhance = executor.submit(() -> {
                barrier.await();
                return newEnhanceUseCase().execute(giverId, swordId, false, newKey());
            });
            Future<long[]> offer = executor.submit(() -> {
                barrier.await();
                new SetTradeOfferUseCase(sessionPort)
                        .execute(sessionId, giverId, 0, List.of(new OfferedItemInput(swordId, 1)));
                long seenLevel = enhanceLevel(swordId);
                ConfirmTradeUseCase confirm = newConfirmTradeUseCase(sessionPort);
                confirm.execute(sessionId, receiverId, newKey());
                ConfirmTradeUseCase.Result executed = confirm.execute(sessionId, giverId, newKey());
                return new long[] {seenLevel, executed == ConfirmTradeUseCase.Result.EXECUTED ? 1L : 0L};
            });
            EnhanceItemUseCase.EnhanceResult enhanceResult = enhance.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long[] offerOutcome = offer.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(offerOutcome[1]).as("vòng %d: giao dịch thi hành", round).isEqualTo(1L);
            assertThat(ownerOf(swordId)).isEqualTo(receiverId);
            assertThat(enhanceLevel(swordId))
                    .as("vòng %d: cấp người nhận được = cấp đã thấy lúc xác nhận (%s)", round, enhanceResult)
                    .isEqualTo(offerOutcome[0]);
            if (enhanceResult == EnhanceItemUseCase.EnhanceResult.ITEM_IN_TRADE) {
                assertThat(readGold(giverId))
                        .as("vòng %d: bị từ chối thì không mất vàng", round)
                        .isEqualTo(START_GOLD);
                assertThat(enhanceLevel(swordId)).isZero();
            } else {
                assertThat(readGold(giverId))
                        .as("vòng %d: đã roll thì đã trả vàng", round)
                        .isLessThan(START_GOLD);
            }
        }
    }

    // ---------------------------------------------------------------- dựng dữ liệu

    private static EnhanceItemUseCase newEnhanceUseCase() {
        EnhancePorts ports = new EnhancePorts(
                new PostgresInventoryAdapter(dataSource),
                new PostgresGoldAdapter(),
                new PostgresAuditAdapter(),
                new PostgresItemTemplateAdapter(dataSource),
                new PostgresInventoryIdempotencyAdapter(),
                new PostgresInventoryTradeOfferAdapter());
        return new EnhanceItemUseCase(ports, new JdbcInventoryTransactionRunner(dataSource));
    }

    private static ConfirmTradeUseCase newConfirmTradeUseCase(PostgresTradeSessionAdapter sessionPort) {
        TradePorts ports = new TradePorts(
                sessionPort,
                new PostgresTradeInventoryAdapter(dataSource),
                new PostgresTradeGoldAdapter(),
                new PostgresTradeAuditAdapter(),
                new PostgresTradeIdempotencyAdapter());
        return new ConfirmTradeUseCase(ports, new JdbcTradeTransactionRunner(dataSource));
    }

    /** Mở phiên, bên nhận chấp nhận và đặt lời chào rỗng — chỉ còn thiếu lời chào của người đưa. */
    private static long openSession(PostgresTradeSessionAdapter sessionPort, long giverId, long receiverId) {
        long sessionId = new RequestTradeUseCase(sessionPort)
                .execute(giverId, receiverId)
                .session()
                .id();
        new AcceptTradeRequestUseCase(sessionPort).execute(sessionId, receiverId);
        new SetTradeOfferUseCase(sessionPort).execute(sessionId, receiverId, 0, List.of());
        return sessionId;
    }

    private static long newCharacter() {
        nameCounter++;
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId,
                "EnhTrade" + nameCounter,
                Faction.HOA,
                UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        long characterId = holder[0].id();
        update(SET_GOLD, START_GOLD, characterId);
        return characterId;
    }

    private static long giveSword(long characterId) {
        return queryLong(INSERT_ITEM, characterId, woodenSwordTemplateId);
    }

    private static long enhanceLevel(long itemId) {
        return queryLong("SELECT enhance_level FROM inventory_item WHERE id = ?", itemId);
    }

    private static long ownerOf(long itemId) {
        return queryLong("SELECT character_id FROM inventory_item WHERE id = ?", itemId);
    }

    private static long readGold(long characterId) {
        return queryLong("SELECT gold FROM character_stat WHERE character_id = ?", characterId);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static void update(String sql, long... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }

    private static long queryLong(String sql, long... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }

    private static void bind(PreparedStatement statement, long... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setLong(i + 1, params[i]);
        }
    }
}
