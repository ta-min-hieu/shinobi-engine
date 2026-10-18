package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
import dev.kage.inventory.application.UseItemUseCase;
import dev.kage.inventory.domain.ItemUseEffect;
import dev.kage.mail.application.ClaimMailUseCase;
import dev.kage.mail.application.MailPorts;
import dev.kage.mail.application.SendMailUseCase;
import dev.kage.mail.domain.MailDraft;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresAuditAdapter;
import dev.kage.server.database.inventory.PostgresGoldAdapter;
import dev.kage.server.database.inventory.PostgresInventoryAdapter;
import dev.kage.server.database.inventory.PostgresInventoryIdempotencyAdapter;
import dev.kage.server.database.inventory.PostgresInventoryTradeOfferAdapter;
import dev.kage.server.database.inventory.PostgresItemTemplateAdapter;
import dev.kage.server.database.mail.JdbcMailTransactionRunner;
import dev.kage.server.database.mail.PostgresMailAuditAdapter;
import dev.kage.server.database.mail.PostgresMailGoldAdapter;
import dev.kage.server.database.mail.PostgresMailIdempotencyAdapter;
import dev.kage.server.database.mail.PostgresMailInventoryGatewayAdapter;
import dev.kage.server.database.mail.PostgresMailRepositoryAdapter;
import dev.kage.server.database.shop.JdbcShopTransactionRunner;
import dev.kage.server.database.shop.PostgresShopAuditAdapter;
import dev.kage.server.database.shop.PostgresShopCatalogAdapter;
import dev.kage.server.database.shop.PostgresShopGoldAdapter;
import dev.kage.server.database.shop.PostgresShopIdempotencyAdapter;
import dev.kage.server.database.shop.PostgresShopInventoryGatewayAdapter;
import dev.kage.server.database.trade.JdbcTradeTransactionRunner;
import dev.kage.server.database.trade.PostgresTradeAuditAdapter;
import dev.kage.server.database.trade.PostgresTradeGoldAdapter;
import dev.kage.server.database.trade.PostgresTradeIdempotencyAdapter;
import dev.kage.server.database.trade.PostgresTradeInventoryAdapter;
import dev.kage.server.database.trade.PostgresTradeSessionAdapter;
import dev.kage.shop.application.BuyItemUseCase;
import dev.kage.shop.application.SellItemUseCase;
import dev.kage.shop.application.ShopPorts;
import dev.kage.shop.domain.ShopListing;
import dev.kage.trade.application.AcceptTradeRequestUseCase;
import dev.kage.trade.application.ConfirmTradeUseCase;
import dev.kage.trade.application.RequestTradeUseCase;
import dev.kage.trade.application.SetTradeOfferUseCase;
import dev.kage.trade.application.TradePorts;
import dev.kage.trade.domain.OfferedItemInput;

/**
 * Kiểm toán đồng thời các đường ghi KINH TẾ chưa có phép đo song song riêng (2026-10-02): mua/bán,
 * nhận thư, xác nhận giao dịch, dùng đan dược, cường hoá. Mỗi test bắn {@link #THREADS} lệnh cùng
 * lúc vào CÙNG một tài sản rồi đo GIÁ TRỊ CUỐI (vàng, số món, cấp, cờ) — bài học PvP rating race:
 * "có chặn không" là phép đo yếu, chỉ giá trị cuối mới lộ lost update / double-spend.
 *
 * <p>Khác {@code InventoryGrantRaceIntegrationTest} (đặt cuộc đua TẤT ĐỊNH giữa hai đường KHÁC
 * nhau): ở đây là nhiều bản sao của CÙNG một lệnh — mỗi bản một {@code idempotencyKey} riêng, vì
 * chống double-click là việc của khoá/compare-and-set, không phải của idempotency (hai lần bấm là
 * hai key). Cổng xuất phát {@link CountDownLatch} cho mọi luồng vào transaction gần như cùng lúc.
 */
@Testcontainers
class EconomyConcurrencyIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int THREADS = 8;
    private static final long TASK_TIMEOUT_SECONDS = 30L;
    private static final long START_GOLD = 10_000L;
    private static final String SET_GOLD = "UPDATE character_stat SET gold = ? WHERE character_id = ?";
    private static final String INSERT_ITEM =
            "INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)"
                    + " VALUES (?, ?, ?, 0, ?)";
    private static final int AFFORDABLE_SWORDS = 3;
    private static final int KUNAI_STACK = 5;
    private static final int PILL_STACK = 3;
    private static final long MAIL_GOLD = 500L;
    private static final long TRADE_GOLD = 10L;
    private static final long SWITCHED_TRADE_GOLD = 20L;
    private static final int TRADE_ROUNDS = 10;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    // volatile: ghi ở @BeforeAll, đọc từ các luồng của executor (SpotBugs AT — long không nguyên tử).
    static volatile long accountId;
    static volatile long woodenSwordTemplateId;
    static volatile long kunaiTemplateId;
    static volatile long pillTemplateId;
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
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "econracetester"));
        accountId = accountHolder[0].id();
        woodenSwordTemplateId = templateIdOf("wooden_sword");
        kunaiTemplateId = templateIdOf("kunai");
        pillTemplateId = templateIdOf("minor_healing_pill");
        executor = Executors.newFixedThreadPool(THREADS);
    }

    @AfterAll
    static void shutdown() {
        executor.shutdownNow();
    }

    /** Đủ vàng cho đúng 3 thanh kiếm, 8 lệnh mua cùng lúc: đúng 3 thành công, vàng về 0, không âm. */
    @Test
    void shouldNeverOverspend_whenManyBuysRaceForTheSameGold() throws Exception {
        long characterId = newCharacter();
        long swordPrice = listingOf(woodenSwordTemplateId).buyPrice();
        setGold(characterId, swordPrice * AFFORDABLE_SWORDS);

        List<Object> results =
                runConcurrently(() -> newBuyUseCase().execute(characterId, woodenSwordTemplateId, 1, newKey()));

        assertThat(countEqual(results, BuyItemUseCase.BuyResult.OK)).isEqualTo(AFFORDABLE_SWORDS);
        assertThat(countEqual(results, BuyItemUseCase.BuyResult.INSUFFICIENT_GOLD))
                .isEqualTo(THREADS - AFFORDABLE_SWORDS);
        assertThat(readGold(characterId)).as("vàng").isZero();
        assertThat(countOf(characterId, woodenSwordTemplateId)).as("số kiếm").isEqualTo(AFFORDABLE_SWORDS);
    }

    /** Một chồng 5 phi đao, 8 lệnh bán 1 cái cùng lúc: đúng 5 lần cộng tiền, chồng biến mất. */
    @Test
    void shouldCreditEachSoldUnitExactlyOnce_whenManySellsRaceForTheSameStack() throws Exception {
        long characterId = newCharacter();
        long stackId = insertItem(characterId, kunaiTemplateId, KUNAI_STACK);
        long sellPrice = listingOf(kunaiTemplateId).sellPrice();

        List<Object> results = runConcurrently(() -> newSellUseCase().execute(characterId, stackId, 1, newKey()));

        assertThat(countEqual(results, SellItemUseCase.SellResult.OK)).isEqualTo(KUNAI_STACK);
        assertThat(countEqual(results, SellItemUseCase.SellResult.INSUFFICIENT_QUANTITY))
                .isEqualTo(THREADS - KUNAI_STACK);
        assertThat(readGold(characterId)).as("vàng").isEqualTo(START_GOLD + sellPrice * KUNAI_STACK);
        assertThat(countOf(characterId, kunaiTemplateId)).as("phi đao còn lại").isZero();
    }

    /** Một lá thư 500 vàng, 8 lệnh nhận (8 key khác nhau) cùng lúc: vàng cộng ĐÚNG một lần. */
    @Test
    void shouldCreditMailGoldExactlyOnce_whenManyClaimsRace() throws Exception {
        long characterId = newCharacter();
        SendMailUseCase.SendResult sent = new SendMailUseCase(newMailPorts(), new JdbcMailTransactionRunner(dataSource))
                .execute(new MailDraft(null, characterId, "T", "B", MAIL_GOLD, List.of()));
        long mailId = sent.mail().orElseThrow().id();

        List<Object> results = runConcurrently(() -> newClaimMailUseCase().execute(mailId, characterId, newKey()));

        assertThat(countEqual(results, ClaimMailUseCase.ClaimOutcome.OK)).isEqualTo(1);
        assertThat(countEqual(results, ClaimMailUseCase.ClaimOutcome.ALREADY_CLAIMED))
                .isEqualTo(THREADS - 1);
        assertThat(readGold(characterId)).as("vàng").isEqualTo(START_GOLD + MAIL_GOLD);
    }

    /**
     * Hai bên bấm xác nhận CÙNG lúc, lặp {@link #TRADE_ROUNDS} phiên: mỗi phiên thi hành đúng một
     * lần, tổng vàng hai người bảo toàn và đúng bằng số phiên × lời chào.
     */
    @Test
    void shouldExecuteEachTradeExactlyOnce_whenBothSidesConfirmTogether() throws Exception {
        long requesterId = newCharacter();
        long targetId = newCharacter();
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        ConfirmTradeUseCase confirm = newConfirmTradeUseCase(sessionPort);

        for (int round = 0; round < TRADE_ROUNDS; round++) {
            long sessionId = openTradeWithRequesterGold(sessionPort, requesterId, targetId, TRADE_GOLD);
            List<Object> results = runPair(
                    () -> confirm.execute(sessionId, requesterId, newKey()),
                    () -> confirm.execute(sessionId, targetId, newKey()));
            assertThat(countEqual(results, ConfirmTradeUseCase.Result.EXECUTED))
                    .as("vòng %d: %s", round, results)
                    .isEqualTo(1);
        }

        assertThat(readGold(requesterId)).isEqualTo(START_GOLD - TRADE_GOLD * TRADE_ROUNDS);
        assertThat(readGold(targetId)).isEqualTo(START_GOLD + TRADE_GOLD * TRADE_ROUNDS);
    }

    /**
     * Người nhận đã xác nhận lời chào 10 vàng; người đưa ĐỔI lời chào thành 20 vàng đúng lúc chính
     * họ bấm xác nhận. Giao dịch chỉ được thi hành trên lời chào người nhận ĐÃ thấy — đổi lời chào
     * phải xoá cờ xác nhận cũ trong cùng khoá dòng phiên.
     */
    @Test
    void shouldOnlyExecuteTheOfferTheOtherSideConfirmed_whenOfferChangesDuringConfirm() throws Exception {
        long requesterId = newCharacter();
        long targetId = newCharacter();
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        ConfirmTradeUseCase confirm = newConfirmTradeUseCase(sessionPort);
        long requesterPaid = 0L;

        for (int round = 0; round < TRADE_ROUNDS; round++) {
            long sessionId = openTradeWithRequesterGold(sessionPort, requesterId, targetId, TRADE_GOLD);
            assertThat(confirm.execute(sessionId, targetId, newKey())).isEqualTo(ConfirmTradeUseCase.Result.OK);
            List<Object> results = runPair(
                    () -> new SetTradeOfferUseCase(sessionPort)
                            .execute(sessionId, requesterId, SWITCHED_TRADE_GOLD, List.of()),
                    () -> confirm.execute(sessionId, requesterId, newKey()));
            if (results.get(1) == ConfirmTradeUseCase.Result.EXECUTED) {
                requesterPaid += TRADE_GOLD;
            }
            new dev.kage.trade.application.CancelTradeUseCase(sessionPort).execute(sessionId, requesterId);
        }

        assertThat(readGold(requesterId)).as("người đưa").isEqualTo(START_GOLD - requesterPaid);
        assertThat(readGold(targetId)).as("người nhận").isEqualTo(START_GOLD + requesterPaid);
    }

    /**
     * Người đưa BÁN đúng chồng phi đao đang chào, cùng lúc với lần bấm xác nhận cuối cùng (rà
     * 2026-10-03): mỗi vòng đúng MỘT trong hai thắng. Đo giá trị cuối — phi đao chỉ có thể nằm ở
     * người nhận (giao dịch thắng) hoặc đã đổi thành vàng của người đưa (bán thắng), không bao giờ cả hai.
     */
    @Test
    void shouldLetEitherTheTradeOrTheSaleHaveTheStack_whenBothRaceForIt() throws Exception {
        long requesterId = newCharacter();
        long targetId = newCharacter();
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        ConfirmTradeUseCase confirm = newConfirmTradeUseCase(sessionPort);
        long sellPrice = listingOf(kunaiTemplateId).sellPrice();
        int executed = 0;
        int sold = 0;

        for (int round = 0; round < TRADE_ROUNDS; round++) {
            long stackId = insertItem(requesterId, kunaiTemplateId, KUNAI_STACK);
            long sessionId = openTradeWithRequesterGold(sessionPort, requesterId, targetId, 0L);
            new SetTradeOfferUseCase(sessionPort)
                    .execute(sessionId, requesterId, 0L, List.of(new OfferedItemInput(stackId, KUNAI_STACK)));
            assertThat(confirm.execute(sessionId, targetId, newKey())).isEqualTo(ConfirmTradeUseCase.Result.OK);

            List<Object> results =
                    runPair(() -> confirm.execute(sessionId, requesterId, newKey()), () -> newSellUseCase()
                            .execute(requesterId, stackId, KUNAI_STACK, newKey()));
            boolean tradeWon = results.get(0) == ConfirmTradeUseCase.Result.EXECUTED;
            boolean saleWon = results.get(1) == SellItemUseCase.SellResult.OK;
            assertThat(tradeWon ^ saleWon).as("vòng %d: %s", round, results).isTrue();
            executed += tradeWon ? 1 : 0;
            sold += saleWon ? 1 : 0;
            new dev.kage.trade.application.CancelTradeUseCase(sessionPort).execute(sessionId, requesterId);
        }

        assertThat(countOf(requesterId, kunaiTemplateId))
                .as("phi đao người đưa")
                .isZero();
        assertThat(countOf(targetId, kunaiTemplateId))
                .as("phi đao người nhận")
                .isEqualTo((long) executed * KUNAI_STACK);
        assertThat(readGold(requesterId)).as("vàng người đưa").isEqualTo(START_GOLD + sold * KUNAI_STACK * sellPrice);
        assertThat(readGold(targetId)).as("vàng người nhận").isEqualTo(START_GOLD);
    }

    /**
     * Người nhận bấm HUỶ đúng lúc người đưa bấm xác nhận cuối cùng: mỗi vòng đúng một trong hai
     * thắng — huỷ thắng thì vàng không nhúc nhích, xác nhận thắng thì chuyển trọn. Không có "huỷ
     * rồi mà tiền vẫn đi" hay "thi hành một nửa".
     */
    @Test
    void shouldEitherCancelOrExecuteWholly_whenCancelRacesTheLastConfirm() throws Exception {
        long requesterId = newCharacter();
        long targetId = newCharacter();
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        ConfirmTradeUseCase confirm = newConfirmTradeUseCase(sessionPort);
        dev.kage.trade.application.CancelTradeUseCase cancel =
                new dev.kage.trade.application.CancelTradeUseCase(sessionPort);
        int executed = 0;

        for (int round = 0; round < TRADE_ROUNDS; round++) {
            long sessionId = openTradeWithRequesterGold(sessionPort, requesterId, targetId, TRADE_GOLD);
            assertThat(confirm.execute(sessionId, targetId, newKey())).isEqualTo(ConfirmTradeUseCase.Result.OK);

            List<Object> results = runPair(
                    () -> confirm.execute(sessionId, requesterId, newKey()), () -> cancel.execute(sessionId, targetId));
            boolean tradeWon = results.get(0) == ConfirmTradeUseCase.Result.EXECUTED;
            boolean cancelWon = results.get(1) == dev.kage.trade.application.CancelTradeUseCase.Result.OK;
            assertThat(tradeWon ^ cancelWon).as("vòng %d: %s", round, results).isTrue();
            executed += tradeWon ? 1 : 0;
            assertThat(sessionPort.findActiveSessionOfCharacter(requesterId)).isEmpty();
        }

        assertThat(readGold(requesterId)).as("người đưa").isEqualTo(START_GOLD - TRADE_GOLD * executed);
        assertThat(readGold(targetId)).as("người nhận").isEqualTo(START_GOLD + TRADE_GOLD * executed);
    }

    /** Ba viên đan, 8 lệnh uống cùng lúc: đúng 3 lần hồi máu, ô túi biến mất, không viên nào "uống ké". */
    @Test
    void shouldConsumeOnePillPerHeal_whenManyUsesRace() throws Exception {
        long characterId = newCharacter();
        long stackId = insertItem(characterId, pillTemplateId, PILL_STACK);
        UseItemUseCase useItem = new UseItemUseCase(
                new UseItemUseCase.Ports(
                        new PostgresInventoryAdapter(dataSource),
                        new PostgresItemTemplateAdapter(dataSource),
                        new PostgresAuditAdapter(),
                        new PostgresInventoryTradeOfferAdapter()),
                new JdbcInventoryTransactionRunner(dataSource));

        List<Object> results = runConcurrently(() -> useItem.execute(characterId, stackId));

        long heals = results.stream()
                .map(UseItemUseCase.UseItemResult.class::cast)
                .filter(r -> r.outcome() == UseItemUseCase.Outcome.OK && r.effect() == ItemUseEffect.HEAL_HP)
                .count();
        assertThat(heals).as("số lần hồi máu").isEqualTo(PILL_STACK);
        assertThat(countOf(characterId, pillTemplateId)).as("đan còn lại").isZero();
        assertThat(countAudit(characterId, "USE_ITEM")).isEqualTo(PILL_STACK);
    }

    /**
     * 8 lệnh cường hoá CÙNG một thanh kiếm +0 cùng lúc (dưới +10 thất bại không rớt cấp): mỗi lần
     * đều tính tiền, và các lần nối nhau thành MỘT chuỗi cấp liền mạch — lost update sẽ làm hai lần
     * cùng xuất phát từ một cấp (một lần thành công bị ghi đè mất) dù cả hai đều đã trả tiền.
     */
    @Test
    void shouldChargeEveryEnhanceAndLoseNoLevel_whenManyEnhancesRace() throws Exception {
        long characterId = newCharacter();
        long swordId = insertItem(characterId, woodenSwordTemplateId, 1);
        EnhanceItemUseCase enhance = new EnhanceItemUseCase(
                new EnhancePorts(
                        new PostgresInventoryAdapter(dataSource),
                        new PostgresGoldAdapter(),
                        new PostgresAuditAdapter(),
                        new PostgresItemTemplateAdapter(dataSource),
                        new PostgresInventoryIdempotencyAdapter(),
                        new PostgresInventoryTradeOfferAdapter()),
                new JdbcInventoryTransactionRunner(dataSource));

        List<Object> results = runConcurrently(() -> enhance.execute(characterId, swordId, false, newKey()));

        long successes = countEqual(results, EnhanceItemUseCase.EnhanceResult.SUCCESS);
        assertThat(successes + countEqual(results, EnhanceItemUseCase.EnhanceResult.FAILED_NO_DROP))
                .as("mọi lần đều chạy thật: %s", results)
                .isEqualTo(THREADS);
        assertThat(queryLong("SELECT enhance_level FROM inventory_item WHERE id = ?", swordId))
                .as("cấp cuối = số lần thành công")
                .isEqualTo(successes);
        List<long[]> chain = enhanceAuditChain(characterId);
        long charged = 0L;
        long expectedFrom = 0L;
        for (long[] link : chain) {
            assertThat(link[0]).as("chuỗi cấp liền mạch").isEqualTo(expectedFrom);
            expectedFrom = link[1];
            charged += link[2];
        }
        assertThat(chain).hasSize(THREADS);
        assertThat(readGold(characterId))
                .as("vàng = đầu − tổng tiền đã ghi audit")
                .isEqualTo(START_GOLD - charged);
    }

    /**
     * Lỗi thật (đo 2026-10-02: 10/10 vòng hỏng): A mời T đúng lúc B mời T — cả hai phép kiểm "T có
     * đang trade không" của use case cùng thấy rỗng, cả hai phiên được tạo, T kẹt trong HAI phiên mà
     * màn trạng thái chỉ thấy một. Đúng một lời mời phải thắng.
     */
    @Test
    void shouldPutTheTargetInOneSessionOnly_whenTwoRequestsForThemRace() throws Exception {
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        for (int round = 0; round < TRADE_ROUNDS; round++) {
            long first = newCharacter();
            long second = newCharacter();
            long target = newCharacter();

            List<Object> results = runPair(
                    () -> new RequestTradeUseCase(sessionPort).execute(first, target),
                    () -> new RequestTradeUseCase(sessionPort).execute(second, target));

            long okCount = results.stream()
                    .map(RequestTradeUseCase.Result.class::cast)
                    .filter(r -> r.outcome() == RequestTradeUseCase.Outcome.OK)
                    .count();
            assertThat(okCount).as("vòng %d: số lời mời thắng", round).isEqualTo(1);
            assertThat(queryLong(
                            "SELECT COUNT(*) FROM trade_session"
                                    + " WHERE requester_character_id = ? OR target_character_id = ?",
                            target,
                            target))
                    .as("vòng %d: số phiên của người được mời", round)
                    .isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------- chạy song song

    /** {@link #THREADS} bản sao của {@code task}, thả cùng lúc qua một cổng xuất phát. */
    private static List<Object> runConcurrently(Callable<Object> task) throws Exception {
        List<Callable<Object>> tasks = new ArrayList<Callable<Object>>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(task);
        }
        return runGated(tasks);
    }

    private static List<Object> runPair(Callable<Object> first, Callable<Object> second) throws Exception {
        return runGated(List.of(first, second));
    }

    private static List<Object> runGated(List<Callable<Object>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<Future<Object>>();
        for (Callable<Object> task : tasks) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        assertThat(ready.await(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        List<Object> results = new ArrayList<Object>();
        for (Future<Object> future : futures) {
            results.add(future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        return results;
    }

    private static long countEqual(List<Object> results, Object expected) {
        return results.stream().filter(expected::equals).count();
    }

    // ---------------------------------------------------------------- dựng use case

    private static BuyItemUseCase newBuyUseCase() {
        return new BuyItemUseCase(newShopPorts(), new JdbcShopTransactionRunner(dataSource));
    }

    private static SellItemUseCase newSellUseCase() {
        return new SellItemUseCase(newShopPorts(), new JdbcShopTransactionRunner(dataSource));
    }

    private static ShopPorts newShopPorts() {
        return new ShopPorts(
                new PostgresShopCatalogAdapter(dataSource),
                new PostgresShopGoldAdapter(),
                new PostgresShopInventoryGatewayAdapter(dataSource),
                new PostgresShopAuditAdapter(),
                new PostgresShopIdempotencyAdapter());
    }

    private static ShopListing listingOf(long itemTemplateId) {
        return new PostgresShopCatalogAdapter(dataSource)
                .findListing(itemTemplateId)
                .orElseThrow();
    }

    private static MailPorts newMailPorts() {
        return new MailPorts(
                new PostgresMailRepositoryAdapter(dataSource),
                new PostgresMailGoldAdapter(),
                new PostgresMailInventoryGatewayAdapter(),
                new PostgresMailAuditAdapter(),
                new PostgresMailIdempotencyAdapter());
    }

    private static ClaimMailUseCase newClaimMailUseCase() {
        return new ClaimMailUseCase(newMailPorts(), new JdbcMailTransactionRunner(dataSource));
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

    /** Mở phiên, chấp nhận, người mời chào {@code gold} vàng, người kia chào rỗng. Chưa ai xác nhận. */
    private static long openTradeWithRequesterGold(
            PostgresTradeSessionAdapter sessionPort, long requesterId, long targetId, long gold) {
        RequestTradeUseCase.Result requested = new RequestTradeUseCase(sessionPort).execute(requesterId, targetId);
        assertThat(requested.outcome()).isEqualTo(RequestTradeUseCase.Outcome.OK);
        long sessionId = requested.session().id();
        assertThat(new AcceptTradeRequestUseCase(sessionPort).execute(sessionId, targetId))
                .isEqualTo(AcceptTradeRequestUseCase.Result.OK);
        assertThat(new SetTradeOfferUseCase(sessionPort).execute(sessionId, requesterId, gold, List.of()))
                .isEqualTo(SetTradeOfferUseCase.Result.OK);
        return sessionId;
    }

    // ---------------------------------------------------------------- dữ liệu

    private static long newCharacter() {
        nameCounter++;
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId,
                "EconRace" + nameCounter,
                Faction.HOA,
                UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        long characterId = holder[0].id();
        setGold(characterId, START_GOLD);
        return characterId;
    }

    private static void setGold(long characterId, long gold) {
        execute(SET_GOLD, gold, characterId);
    }

    private static long insertItem(long characterId, long itemTemplateId, int quantity) {
        execute(INSERT_ITEM, characterId, itemTemplateId, quantity, 0L);
        return queryLong(
                "SELECT MAX(id) FROM inventory_item WHERE character_id = ? AND item_template_id = ?",
                characterId,
                itemTemplateId);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static long readGold(long characterId) {
        return queryLong("SELECT gold FROM character_stat WHERE character_id = ?", characterId);
    }

    private static long countOf(long characterId, long itemTemplateId) {
        return queryLong(
                "SELECT COALESCE(SUM(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?",
                characterId,
                itemTemplateId);
    }

    private static long countAudit(long characterId, String action) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = ?")) {
            statement.setLong(1, characterId);
            statement.setString(2, action);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm audit", e);
        }
    }

    /** Mỗi lần cường hoá theo thứ tự commit: {fromLevel, toLevel, goldCost}. */
    private static List<long[]> enhanceAuditChain(long characterId) {
        String sql = "SELECT (detail_json::jsonb->>'fromLevel')::bigint, (detail_json::jsonb->>'toLevel')::bigint,"
                + " (detail_json::jsonb->>'goldCost')::bigint"
                + " FROM audit_log WHERE character_id = ? AND action = 'ENHANCE_ITEM' ORDER BY id";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, characterId);
            List<long[]> chain = new ArrayList<long[]>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    chain.add(new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)});
                }
            }
            return chain;
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc audit cường hoá", e);
        }
    }

    private static void execute(String sql, long... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setLong(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }

    private static long queryLong(String sql, long... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setLong(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }

    private static long templateIdOf(String code) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM item_template WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc item_template: " + code, e);
        }
    }
}
