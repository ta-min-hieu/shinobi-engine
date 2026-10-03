package dev.kage.server.database.inventory;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EnhancePorts;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.mail.application.ClaimMailUseCase;
import dev.kage.mail.application.MailPorts;
import dev.kage.mail.application.SendMailUseCase;
import dev.kage.mail.domain.MailAttachmentDraft;
import dev.kage.mail.domain.MailDraft;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ProcessQuestEventUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.mail.JdbcMailTransactionRunner;
import dev.kage.server.database.mail.PostgresMailAuditAdapter;
import dev.kage.server.database.mail.PostgresMailGoldAdapter;
import dev.kage.server.database.mail.PostgresMailIdempotencyAdapter;
import dev.kage.server.database.mail.PostgresMailInventoryGatewayAdapter;
import dev.kage.server.database.mail.PostgresMailRepositoryAdapter;
import dev.kage.server.database.quest.JdbcQuestTransactionRunner;
import dev.kage.server.database.quest.PostgresQuestAuditAdapter;
import dev.kage.server.database.quest.PostgresQuestExpAdapter;
import dev.kage.server.database.quest.PostgresQuestIdempotencyAdapter;
import dev.kage.server.database.quest.PostgresQuestInventoryGrantAdapter;
import dev.kage.server.database.quest.PostgresQuestItemTakeAdapter;
import dev.kage.server.database.quest.PostgresQuestProgressAdapter;
import dev.kage.server.database.quest.PostgresQuestTemplateAdapter;
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
import dev.kage.trade.application.AcceptTradeRequestUseCase;
import dev.kage.trade.application.ConfirmTradeUseCase;
import dev.kage.trade.application.RequestTradeUseCase;
import dev.kage.trade.application.SetTradeOfferUseCase;
import dev.kage.trade.application.TradePorts;
import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeSession;

/**
 * Hai đường cấp vật phẩm KHÁC NHAU chạy song song cho CÙNG một nhân vật không được đẩy túi quá 20
 * ô, và không được làm mất gì trong im lặng.
 *
 * <p><b>Lỗi thật (2026-09-25).</b> Cửa hàng, thư, thưởng quest, giao dịch và rơi đồ mỗi đường tự
 * "đếm ô rồi INSERT" trong transaction RIÊNG, không có điểm tuần tự hoá chung. Khoá
 * {@code ReentrantLock} của {@code InventoryFacade} chỉ phủ các lệnh đi qua facade (rơi đồ, dùng
 * đồ, cường hoá) — cửa hàng/thư/quest/giao dịch không đi qua đó. READ COMMITTED: hai transaction
 * cùng đếm thấy 19, cùng chèn, cùng commit ⇒ 21 ô. Tệ hơn với đường facade: nó đọc túi NGOÀI
 * transaction rồi {@code save()} xoá mọi dòng "không có trong bản đã đọc" — tức XOÁ THẬT món vừa mua
 * ở cửa hàng (tiền đã trừ).
 *
 * <p>Đo GIÁ TRỊ CUỐI (bài học PvP rating race): số dòng trong túi, số kiếm, vàng, cờ đã nhận thư —
 * không đo "có chặn không". Cuộc đua được đặt TẤT ĐỊNH (xem
 * {@link #runSecondWhileFirstHoldsItsGrant}), không trông vào may rủi của bộ lập lịch.
 */
@Testcontainers
class InventoryGrantRaceIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int BAG_CAPACITY = 20;
    private static final int DEADLOCK_ROUNDS = 40;
    private static final long START_GOLD = 10_000L;
    private static final String SET_GOLD = "UPDATE character_stat SET gold = ? WHERE character_id = ?";
    private static final String INSERT_ITEM =
            "INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)"
                    + " VALUES (?, ?, ?, 0, ?)";
    private static final long SWORD_BUY_PRICE = 100L;
    private static final long TASK_TIMEOUT_SECONDS = 30L;
    private static final long INTERLEAVE_TIMEOUT_SECONDS = 10L;
    private static final String TRAINING_QUEST_CODE = "kill_training_dummies";
    private static final long TRAINING_QUEST_KUNAI = 5L;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    // volatile: ghi ở @BeforeAll, đọc từ các luồng của executor — ghi biến 64-bit không volatile không
    // bảo đảm nguyên tử (SpotBugs AT).
    static volatile long accountId;
    static volatile long woodenSwordTemplateId;
    static volatile long kunaiTemplateId;
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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "grantracetester"));
        accountId = accountHolder[0].id();
        woodenSwordTemplateId = templateIdOf("wooden_sword");
        kunaiTemplateId = templateIdOf("kunai");
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterAll
    static void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Cửa hàng + nhận thư, mỗi bên cần đúng 1 ô, túi còn đúng 1 ô: đúng MỘT bên được, bên kia bị từ
     * chối SẠCH (vàng/thư không đổi).
     */
    @Test
    void shouldNeverExceedCapacity_whenShopBuyAndMailClaimRaceForTheLastSlot() throws Exception {
        long characterId = characterWithSwords(BAG_CAPACITY - 1);
        long mailId = sendSystemMailWithOneSword(characterId);

        List<Object> results = runSecondWhileFirstHoldsItsGrant(
                () -> newBuyUseCase().execute(characterId, woodenSwordTemplateId, 1, newKey()),
                () -> newClaimMailUseCase().execute(mailId, characterId, newKey()));
        BuyItemUseCase.BuyResult buy = (BuyItemUseCase.BuyResult) results.get(0);
        ClaimMailUseCase.ClaimOutcome claim = (ClaimMailUseCase.ClaimOutcome) results.get(1);

        boolean bought = buy == BuyItemUseCase.BuyResult.OK;
        boolean claimed = claim == ClaimMailUseCase.ClaimOutcome.OK;
        assertThat(countRows(characterId)).as("số ô").isLessThanOrEqualTo(BAG_CAPACITY);
        assertThat(bought ^ claimed)
                .as("đúng một bên thắng (%s / %s)", buy, claim)
                .isTrue();
        assertThat(countSwords(characterId)).as("số kiếm").isEqualTo(BAG_CAPACITY);
        assertThat(readGold(characterId)).as("vàng").isEqualTo(START_GOLD - (bought ? SWORD_BUY_PRICE : 0));
        assertThat(isMailClaimed(mailId)).as("cờ đã nhận thư").isEqualTo(claimed);
    }

    /**
     * Cửa hàng + thưởng quest. Quest kiểm "đủ chỗ" ở một transaction CHỈ ĐỌC riêng rồi mới trao, và
     * bỏ qua kết quả của lần trao — túi đầy lúc trao là mất thưởng mà quest vẫn thành CLAIMED.
     */
    @Test
    void shouldNeverExceedCapacityNorLoseTheReward_whenShopBuyAndQuestClaimRace() throws Exception {
        long characterId = characterWithSwords(BAG_CAPACITY - 1);
        completeTrainingDummies(characterId);

        List<Object> results = runSecondWhileFirstHoldsItsGrant(
                () -> newBuyUseCase().execute(characterId, woodenSwordTemplateId, 1, newKey()),
                () -> newClaimQuestUseCase().execute(characterId, TRAINING_QUEST_CODE, newKey()));
        boolean bought = results.get(0) == BuyItemUseCase.BuyResult.OK;
        ClaimQuestRewardUseCase.ClaimResult claim = (ClaimQuestRewardUseCase.ClaimResult) results.get(1);
        boolean claimed = claim.outcome() == ClaimQuestRewardUseCase.ClaimResult.Outcome.OK;

        assertThat(countRows(characterId)).as("số ô").isLessThanOrEqualTo(BAG_CAPACITY);
        assertThat(bought ^ claimed)
                .as("đúng một bên thắng (%s / %s)", results.get(0), claim.outcome())
                .isTrue();
        assertThat(countOf(characterId, kunaiTemplateId))
                .as("phi đao thưởng quest")
                .isEqualTo(claimed ? TRAINING_QUEST_KUNAI : 0L);
        assertThat(questStatus(characterId)).as("trạng thái quest").isEqualTo(claimed ? "CLAIMED" : "COMPLETED");
    }

    /**
     * Giao dịch (người kia đưa 1 thanh kiếm) + cửa hàng, túi người nhận còn 1 ô. Giao dịch kiểm sức
     * chứa SAU mọi lượt chuyển — vẫn phải thấy món cửa hàng vừa chèn.
     */
    @Test
    void shouldNeverExceedCapacity_whenShopBuyAndTradeRaceForTheLastSlot() throws Exception {
        long receiverId = characterWithSwords(BAG_CAPACITY - 1);
        long giverId = characterWithSwords(1);
        long giverSwordId = itemIds(giverId, woodenSwordTemplateId).get(0);
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        TradeSession session = new RequestTradeUseCase(sessionPort)
                .execute(giverId, receiverId)
                .session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), receiverId);
        new SetTradeOfferUseCase(sessionPort)
                .execute(session.id(), giverId, 0, List.of(new OfferedItemInput(giverSwordId, 1)));
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), receiverId, 0, List.of());
        ConfirmTradeUseCase confirm = newConfirmTradeUseCase(sessionPort);
        confirm.execute(session.id(), giverId, newKey());

        List<Object> results = runSecondWhileFirstHoldsItsGrant(
                () -> newBuyUseCase().execute(receiverId, woodenSwordTemplateId, 1, newKey()),
                () -> confirm.execute(session.id(), receiverId, newKey()));
        boolean bought = results.get(0) == BuyItemUseCase.BuyResult.OK;
        boolean traded = results.get(1) == ConfirmTradeUseCase.Result.EXECUTED;

        assertThat(countRows(receiverId)).as("số ô").isLessThanOrEqualTo(BAG_CAPACITY);
        assertThat(bought ^ traded)
                .as("đúng một bên thắng (%s / %s)", results.get(0), results.get(1))
                .isTrue();
        assertThat(countSwords(receiverId) + countSwords(giverId))
                .as("tổng kiếm hai người")
                .isEqualTo(BAG_CAPACITY + (bought ? 1L : 0L));
    }

    /**
     * Đường facade ({@link AddItemUseCase}, cùng khuôn đọc-sửa-lưu với rơi đồ): cửa hàng chen vào
     * ĐÚNG giữa lúc facade đã đọc túi và lúc nó lưu. Món đã trả tiền không được biến mất.
     *
     * <p>Chen bằng {@link ItemTemplatePort}: {@code AddItemUseCase} tra {@code max_stack} sau khi đọc
     * túi và trước khi lưu — móc vào đó là chỗ duy nhất đặt được cuộc đua một cách tất định mà
     * không sửa mã sản phẩm. Móc chờ tới khi lần mua xong HẲN hoặc đang đứng chờ khoá.
     */
    @Test
    void shouldNeverDeleteAPurchasedItem_whenShopBuyLandsBetweenTheFacadeLoadAndSave() throws Exception {
        long characterId = characterWithSwords(BAG_CAPACITY - 2);
        AtomicReference<Future<Object>> buyHolder = new AtomicReference<Future<Object>>();
        PostgresItemTemplateAdapter realTemplates = new PostgresItemTemplateAdapter(dataSource);
        ItemTemplatePort interleavingTemplates = new ItemTemplatePort() {
            @Override
            public Optional<ItemTemplate> findById(long itemTemplateId) {
                if (buyHolder.get() == null) {
                    Future<Object> buy = executor.submit(
                            () -> newBuyUseCase().execute(characterId, woodenSwordTemplateId, 1, newKey()));
                    buyHolder.set(buy);
                    awaitDoneOrLockWaiters(buy, 1);
                }
                return realTemplates.findById(itemTemplateId);
            }

            @Override
            public Optional<Long> findIdByCode(String code) {
                return realTemplates.findIdByCode(code);
            }
        };
        AddItemUseCase addItem = new AddItemUseCase(
                new PostgresInventoryAdapter(dataSource),
                interleavingTemplates,
                new JdbcInventoryTransactionRunner(dataSource));

        AddItemUseCase.AddItemResult added =
                addItem.execute(characterId, ItemStack.newItem(woodenSwordTemplateId, 1, 0));
        Object buy = buyHolder.get().get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(added).isEqualTo(AddItemUseCase.AddItemResult.OK);
        assertThat(buy).isEqualTo(BuyItemUseCase.BuyResult.OK);
        assertThat(countRows(characterId)).as("số ô").isEqualTo(BAG_CAPACITY);
        assertThat(countSwords(characterId)).as("số kiếm: 18 + 1 thêm + 1 MUA").isEqualTo(BAG_CAPACITY);
        assertThat(readGold(characterId)).isEqualTo(START_GOLD - SWORD_BUY_PRICE);
    }

    /**
     * Không deadlock: mọi đường ghi túi đồ + vàng (mua, bán, gửi thư, nhận thư, cường hoá...) bắn
     * song song vào CÙNG hai nhân vật, theo cả hai chiều, nhiều lần. Postgres phát hiện deadlock
     * bằng cách GIẾT một transaction (ném lỗi) — nên "không ném gì" chính là phép đo; thêm trần
     * thời gian để một lần chờ vô hạn (nếu có) cũng làm test đỏ thay vì treo.
     */
    @Test
    void shouldNotDeadlock_whenEveryWritePathRunsConcurrentlyInBothOrders() throws Exception {
        long first = characterWithSwords(4);
        long second = characterWithSwords(4);
        persistKunai(first, 50);
        persistKunai(second, 50);
        List<Future<Object>> futures = new ArrayList<Future<Object>>();
        for (int iteration = 0; iteration < DEADLOCK_ROUNDS; iteration++) {
            long self = iteration % 2 == 0 ? first : second;
            long other = self == first ? second : first;
            futures.add(executor.submit(() -> newBuyUseCase().execute(self, kunaiTemplateId, 1, newKey())));
            futures.add(executor.submit(() -> sellOneSword(self)));
            futures.add(executor.submit(() -> mailKunaiAndGold(self, other)));
            futures.add(executor.submit(() -> claimAllMail(other)));
            futures.add(executor.submit(
                    () -> newAddItemUseCase().execute(other, ItemStack.newItem(kunaiTemplateId, 1, 0))));
            futures.add(executor.submit(() -> tradeOneKunai(self, other)));
            futures.add(executor.submit(() -> enhanceFirstSword(other)));
        }
        // Chờ HẾT mọi tác vụ rồi mới phán: dừng ở lỗi đầu tiên sẽ để tác vụ còn lại chạy tiếp trên
        // executor dùng chung và làm bẩn test sau (chúng đếm phiên đang chờ khoá).
        List<Throwable> failures = new ArrayList<Throwable>();
        for (Future<Object> future : futures) {
            try {
                future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        assertThat(failures).as("deadlock hoặc lỗi DB khác").isEmpty();
        assertThat(countRows(first)).isLessThanOrEqualTo(BAG_CAPACITY);
        assertThat(countRows(second)).isLessThanOrEqualTo(BAG_CAPACITY);
    }

    // ---------------------------------------------------------------- các thao tác cho test deadlock

    private static Object enhanceFirstSword(long characterId) {
        List<Long> swordIds = itemIds(characterId, woodenSwordTemplateId);
        if (swordIds.isEmpty()) {
            return "no-sword";
        }
        EnhancePorts ports = new EnhancePorts(
                new PostgresInventoryAdapter(dataSource),
                new PostgresGoldAdapter(),
                new PostgresAuditAdapter(),
                new PostgresItemTemplateAdapter(dataSource),
                new PostgresInventoryIdempotencyAdapter(),
                new PostgresInventoryTradeOfferAdapter());
        return new EnhanceItemUseCase(ports, new JdbcInventoryTransactionRunner(dataSource))
                .execute(characterId, swordIds.get(0), false, newKey());
    }

    private static Object sellOneSword(long characterId) {
        List<Long> swordIds = itemIds(characterId, woodenSwordTemplateId);
        if (swordIds.isEmpty()) {
            return "no-sword";
        }
        return newSellUseCase().execute(characterId, swordIds.get(0), 1, newKey());
    }

    private static Object mailKunaiAndGold(long senderId, long recipientId) {
        return newSendMailUseCase()
                .execute(new MailDraft(
                        senderId, recipientId, "T", "B", 1L, List.of(new MailAttachmentDraft(kunaiTemplateId, 1))));
    }

    private static Object claimAllMail(long recipientId) {
        List<Object> outcomes = new ArrayList<Object>();
        for (long mailId : unclaimedMailIds(recipientId)) {
            outcomes.add(newClaimMailUseCase().execute(mailId, recipientId, newKey()));
        }
        return outcomes;
    }

    /** Đổi 1 phi đao lấy 1 phi đao — đủ để khoá vàng + túi của CẢ HAI người trong một transaction. */
    private static Object tradeOneKunai(long requesterId, long targetId) {
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        RequestTradeUseCase.Result requested = new RequestTradeUseCase(sessionPort).execute(requesterId, targetId);
        if (requested.session() == null) {
            return requested; // một phiên khác của cặp này đang mở — bình thường trong cuộc đua này.
        }
        long sessionId = requested.session().id();
        List<Long> requesterKunai = itemIds(requesterId, kunaiTemplateId);
        List<Long> targetKunai = itemIds(targetId, kunaiTemplateId);
        if (requesterKunai.isEmpty() || targetKunai.isEmpty()) {
            return requested;
        }
        new AcceptTradeRequestUseCase(sessionPort).execute(sessionId, targetId);
        new SetTradeOfferUseCase(sessionPort)
                .execute(sessionId, requesterId, 1, List.of(new OfferedItemInput(requesterKunai.get(0), 1)));
        new SetTradeOfferUseCase(sessionPort)
                .execute(sessionId, targetId, 1, List.of(new OfferedItemInput(targetKunai.get(0), 1)));
        ConfirmTradeUseCase confirm = newConfirmTradeUseCase(sessionPort);
        confirm.execute(sessionId, requesterId, newKey());
        ConfirmTradeUseCase.Result result = confirm.execute(sessionId, targetId, newKey());
        new dev.kage.trade.application.CancelTradeUseCase(sessionPort).execute(sessionId, requesterId);
        return result;
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

    private static SendMailUseCase newSendMailUseCase() {
        return new SendMailUseCase(newMailPorts(), new JdbcMailTransactionRunner(dataSource));
    }

    private static AddItemUseCase newAddItemUseCase() {
        return new AddItemUseCase(
                new PostgresInventoryAdapter(dataSource),
                new PostgresItemTemplateAdapter(dataSource),
                new JdbcInventoryTransactionRunner(dataSource));
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

    private static ClaimQuestRewardUseCase newClaimQuestUseCase() {
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(),
                new PostgresQuestInventoryGrantAdapter(),
                new PostgresQuestAuditAdapter(),
                new PostgresQuestIdempotencyAdapter());
        return new ClaimQuestRewardUseCase(
                new PostgresQuestProgressAdapter(dataSource),
                new PostgresQuestTemplateAdapter(dataSource),
                rewardPorts,
                new JdbcQuestTransactionRunner(dataSource));
    }

    /** Nhận quest đầu tiên và đánh đủ 3 mộc nhân ⇒ COMPLETED, chưa lĩnh thưởng. */
    private static void completeTrainingDummies(long characterId) {
        new AcceptQuestUseCase(
                        new PostgresQuestProgressAdapter(dataSource),
                        new PostgresQuestTemplateAdapter(dataSource),
                        new JdbcQuestTransactionRunner(dataSource))
                .execute(characterId, TRAINING_QUEST_CODE);
        ProcessQuestEventUseCase process = new ProcessQuestEventUseCase(
                new PostgresQuestProgressAdapter(dataSource),
                new PostgresQuestItemTakeAdapter(),
                new PostgresQuestAuditAdapter(),
                new JdbcQuestTransactionRunner(dataSource));
        for (int kill = 0; kill < 3; kill++) {
            process.monsterKilled(characterId, "training_dummy");
        }
        assertThat(questStatus(characterId)).isEqualTo("COMPLETED");
    }

    // ---------------------------------------------------------------- chạy song song

    /**
     * Đặt cuộc đua một cách TẤT ĐỊNH, không cần sửa mã sản phẩm: một kết nối riêng khoá bảng
     * {@code audit_log} (mọi đường cấp vật phẩm ở đây đều ghi audit SAU khi đã đếm ô + chèn). Chạy
     * {@code first} tới khi nó đứng chờ ở câu ghi audit — tức đã chèn món của nó nhưng CHƯA commit —
     * rồi mới chạy {@code second}, chờ {@code second} cũng đứng chờ (hoặc xong hẳn), rồi nhả khoá.
     *
     * <p>Không có điểm tuần tự hoá chung: {@code second} đếm thấy 19 (món của {@code first} chưa
     * commit nên vô hình), chèn, và cả hai cùng commit ⇒ 21. Có điểm tuần tự hoá: {@code second} đứng
     * chờ ngay đầu transaction, tới lượt thì đếm thấy 20 và từ chối.
     */
    private static List<Object> runSecondWhileFirstHoldsItsGrant(Supplier<Object> first, Supplier<Object> second)
            throws InterruptedException, ExecutionException, TimeoutException, SQLException {
        Future<Object> firstFuture;
        Future<Object> secondFuture;
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock =
                    blocker.prepareStatement("LOCK TABLE audit_log IN SHARE ROW EXCLUSIVE MODE")) {
                lock.execute();
            }
            firstFuture = executor.submit(first::get);
            awaitDoneOrLockWaiters(firstFuture, 1);
            assertThat(firstFuture.isDone())
                    .as("bên thứ nhất phải đứng chờ ở câu ghi audit")
                    .isFalse();
            secondFuture = executor.submit(second::get);
            awaitDoneOrLockWaiters(secondFuture, 2);
            blocker.commit();
        }
        return List.of(
                firstFuture.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                secondFuture.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    /** Chờ tới khi {@code task} xong, hoặc có ít nhất {@code waiters} phiên DB đang chờ khoá. */
    private static void awaitDoneOrLockWaiters(Future<?> task, int waiters) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(INTERLEAVE_TIMEOUT_SECONDS);
        while (!task.isDone() && lockWaiters() < waiters) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Không đạt được thế xen kẽ mong muốn trong thời hạn");
            }
            Thread.onSpinWait();
        }
    }

    private static long lockWaiters() {
        return queryLong("SELECT COUNT(*) FROM pg_stat_activity"
                + " WHERE wait_event_type = 'Lock' AND datname = current_database()");
    }

    // ---------------------------------------------------------------- dữ liệu

    private static long characterWithSwords(int swords) {
        nameCounter++;
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId,
                "GrantRace" + nameCounter,
                Faction.HOA,
                UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        long characterId = holder[0].id();
        execute(SET_GOLD, START_GOLD, characterId);
        for (int slot = 0; slot < swords; slot++) {
            execute(INSERT_ITEM, characterId, woodenSwordTemplateId, 1L, slot);
        }
        return characterId;
    }

    private static void persistKunai(long characterId, int quantity) {
        execute(INSERT_ITEM, characterId, kunaiTemplateId, quantity, BAG_CAPACITY);
    }

    private static long sendSystemMailWithOneSword(long recipientId) {
        SendMailUseCase.SendResult result = newSendMailUseCase()
                .execute(new MailDraft(
                        null,
                        recipientId,
                        "T",
                        "B",
                        // Thư CHỈ có vật phẩm, không vàng: thư có vàng tình cờ đứng chờ khoá dòng
                        // character_stat mà cửa hàng đang giữ (trừ vàng) nên tự xếp hàng — che mất
                        // cuộc đua thay vì chứng minh không có nó.
                        0L,
                        List.of(new MailAttachmentDraft(woodenSwordTemplateId, 1))));
        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
        return result.mail().orElseThrow().id();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
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

    private static List<Long> queryIds(String sql, long... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setLong(i + 1, params[i]);
            }
            List<Long> ids = new ArrayList<Long>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
            return ids;
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

    private static long countRows(long characterId) {
        return queryLong("SELECT COUNT(*) FROM inventory_item WHERE character_id = ?", characterId);
    }

    private static long countSwords(long characterId) {
        return queryLong(
                "SELECT COALESCE(SUM(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?",
                characterId,
                woodenSwordTemplateId);
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

    private static String questStatus(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT p.status FROM character_quest_progress p"
                                + " JOIN quest_template t ON t.id = p.quest_template_id"
                                + " WHERE p.character_id = ? AND t.code = ?")) {
            statement.setLong(1, characterId);
            statement.setString(2, TRAINING_QUEST_CODE);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc trạng thái quest", e);
        }
    }

    private static boolean isMailClaimed(long mailId) {
        return queryLong("SELECT COUNT(*) FROM mail WHERE id = ? AND claimed_at IS NOT NULL", mailId) > 0;
    }

    private static List<Long> itemIds(long characterId, long itemTemplateId) {
        return queryIds(
                "SELECT id FROM inventory_item WHERE character_id = ? AND item_template_id = ? ORDER BY id",
                characterId,
                itemTemplateId);
    }

    private static List<Long> unclaimedMailIds(long recipientId) {
        return queryIds("SELECT id FROM mail WHERE recipient_id = ? AND claimed_at IS NULL ORDER BY id", recipientId);
    }
}
