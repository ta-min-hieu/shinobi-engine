package dev.kage.server.database;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import redis.clients.jedis.RedisClient;

import dev.kage.account.api.AccountFacade;
import dev.kage.account.application.LoginOrCreateAccountUseCase;
import dev.kage.achievement.api.AchievementFacade;
import dev.kage.achievement.application.AchievementUseCase;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.api.SkillRankFacade;
import dev.kage.character.application.CharacterUseCases;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.application.SelectFactionUseCase;
import dev.kage.character.application.SkillRankUseCase;
import dev.kage.chat.api.ChatFacade;
import dev.kage.chat.application.SendChatMessageUseCase;
import dev.kage.checkin.api.CheckInFacade;
import dev.kage.checkin.application.ClaimDailyCheckInUseCase;
import dev.kage.checkin.domain.CheckInRewardSender;
import dev.kage.crafting.api.CraftingFacade;
import dev.kage.crafting.application.CraftItemUseCase;
import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.dialogue.api.NpcPlacementFacade;
import dev.kage.dialogue.application.GetDialogueNodeUseCase;
import dev.kage.friend.api.FriendFacade;
import dev.kage.friend.application.AcceptFriendRequestUseCase;
import dev.kage.friend.application.FriendRequestCooldowns;
import dev.kage.friend.application.FriendUseCases;
import dev.kage.friend.application.ListFriendsUseCase;
import dev.kage.friend.application.ListPendingFriendRequestsUseCase;
import dev.kage.friend.application.RejectFriendRequestUseCase;
import dev.kage.friend.application.RemoveFriendUseCase;
import dev.kage.friend.application.SendFriendRequestUseCase;
import dev.kage.gm.api.GameEventFacade;
import dev.kage.gm.api.GmFacade;
import dev.kage.gm.domain.GameEventKind;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.GuildProgressionFacade;
import dev.kage.guild.api.GuildShopFacade;
import dev.kage.guild.application.ApproveGuildJoinRequestUseCase;
import dev.kage.guild.application.CreateGuildUseCase;
import dev.kage.guild.application.DisbandGuildUseCase;
import dev.kage.guild.application.FindMyGuildUseCase;
import dev.kage.guild.application.GuildProgressionUseCase;
import dev.kage.guild.application.GuildUseCases;
import dev.kage.guild.application.KickGuildMemberUseCase;
import dev.kage.guild.application.LeaveGuildUseCase;
import dev.kage.guild.application.ListGuildMembersUseCase;
import dev.kage.guild.application.ListPendingGuildJoinRequestsUseCase;
import dev.kage.guild.application.RejectGuildJoinRequestUseCase;
import dev.kage.guild.application.SendGuildJoinRequestUseCase;
import dev.kage.inventory.api.EquipmentUseCases;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.api.InventoryQueryUseCases;
import dev.kage.inventory.api.ItemAcquisitionUseCases;
import dev.kage.inventory.api.ItemActionUseCases;
import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EnhancePorts;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.GetItemCatalogUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.application.ListInventoryUseCase;
import dev.kage.inventory.application.SocketGemUseCase;
import dev.kage.inventory.application.StorageUseCase;
import dev.kage.inventory.application.UnequipItemUseCase;
import dev.kage.inventory.application.UseItemUseCase;
import dev.kage.mail.api.MailFacade;
import dev.kage.mail.application.ClaimMailUseCase;
import dev.kage.mail.application.DeleteMailUseCase;
import dev.kage.mail.application.ListMailUseCase;
import dev.kage.mail.application.MailPorts;
import dev.kage.mail.application.SendMailUseCase;
import dev.kage.market.api.MarketFacade;
import dev.kage.market.application.MarketUseCase;
import dev.kage.monster.api.MonsterFacade;
import dev.kage.party.api.PartyFacade;
import dev.kage.party.application.AcceptPartyInviteUseCase;
import dev.kage.party.application.CreatePartyUseCase;
import dev.kage.party.application.FindMyPartyUseCase;
import dev.kage.party.application.InvitePartyMemberUseCase;
import dev.kage.party.application.KickPartyMemberUseCase;
import dev.kage.party.application.LeavePartyUseCase;
import dev.kage.party.application.ListPartyMembersUseCase;
import dev.kage.party.application.ListPendingPartyInvitesUseCase;
import dev.kage.party.application.PartyUseCases;
import dev.kage.party.application.RejectPartyInviteUseCase;
import dev.kage.pvp.api.PvpFacade;
import dev.kage.quest.api.QuestFacade;
import dev.kage.quest.application.AbandonQuestUseCase;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ListQuestsUseCase;
import dev.kage.quest.application.ProcessQuestEventUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.achievement.PostgresAchievementAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.character.PostgresFactionChangeAdapter;
import dev.kage.server.database.character.PostgresSkillRankAdapter;
import dev.kage.server.database.chat.PostgresChatAuditAdapter;
import dev.kage.server.database.chat.PostgresChatMuteAdapter;
import dev.kage.server.database.checkin.PostgresCheckInAdapter;
import dev.kage.server.database.crafting.JdbcCraftingTransactionRunner;
import dev.kage.server.database.crafting.PostgresCraftingWorkshopAdapter;
import dev.kage.server.database.crafting.PostgresRecipeAdapter;
import dev.kage.server.database.dialogue.PostgresNpcPlacementAdapter;
import dev.kage.server.database.dialogue.PostgresNpcTemplateAdapter;
import dev.kage.server.database.friend.PostgresFriendAdapter;
import dev.kage.server.database.gm.PostgresGameEventAdapter;
import dev.kage.server.database.gm.PostgresGmAuditAdapter;
import dev.kage.server.database.gm.PostgresGmRoleAdapter;
import dev.kage.server.database.guild.PostgresGuildAdapter;
import dev.kage.server.database.guild.PostgresGuildProgressionAdapter;
import dev.kage.server.database.guild.PostgresGuildShopAdapter;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresAuditAdapter;
import dev.kage.server.database.inventory.PostgresCompanionAdapter;
import dev.kage.server.database.inventory.PostgresDropTableAdapter;
import dev.kage.server.database.inventory.PostgresEquipmentAdapter;
import dev.kage.server.database.inventory.PostgresGemAdapter;
import dev.kage.server.database.inventory.PostgresGoldAdapter;
import dev.kage.server.database.inventory.PostgresInventoryAdapter;
import dev.kage.server.database.inventory.PostgresInventoryIdempotencyAdapter;
import dev.kage.server.database.inventory.PostgresInventoryTradeOfferAdapter;
import dev.kage.server.database.inventory.PostgresItemSetAdapter;
import dev.kage.server.database.inventory.PostgresItemTemplateAdapter;
import dev.kage.server.database.inventory.PostgresStorageAdapter;
import dev.kage.server.database.inventory.ThreadLocalGemRoll;
import dev.kage.server.database.mail.JdbcMailTransactionRunner;
import dev.kage.server.database.mail.PostgresMailAuditAdapter;
import dev.kage.server.database.mail.PostgresMailGoldAdapter;
import dev.kage.server.database.mail.PostgresMailIdempotencyAdapter;
import dev.kage.server.database.mail.PostgresMailInventoryGatewayAdapter;
import dev.kage.server.database.mail.PostgresMailRepositoryAdapter;
import dev.kage.server.database.market.JdbcMarketTransactionRunner;
import dev.kage.server.database.market.PostgresMarketAdapter;
import dev.kage.server.database.market.PostgresMarketGoldAdapter;
import dev.kage.server.database.market.PostgresMarketInventoryAdapter;
import dev.kage.server.database.market.PostgresMarketRecordAdapter;
import dev.kage.server.database.monster.PostgresMonsterCatalogAdapter;
import dev.kage.server.database.party.PostgresPartyAdapter;
import dev.kage.server.database.pvp.PostgresPvpRatingAdapter;
import dev.kage.server.database.quest.JdbcQuestTransactionRunner;
import dev.kage.server.database.quest.PostgresQuestAuditAdapter;
import dev.kage.server.database.quest.PostgresQuestExpAdapter;
import dev.kage.server.database.quest.PostgresQuestGoldAdapter;
import dev.kage.server.database.quest.PostgresQuestIdempotencyAdapter;
import dev.kage.server.database.quest.PostgresQuestInventoryGrantAdapter;
import dev.kage.server.database.quest.PostgresQuestItemTakeAdapter;
import dev.kage.server.database.quest.PostgresQuestProgressAdapter;
import dev.kage.server.database.quest.PostgresQuestTemplateAdapter;
import dev.kage.server.database.session.RedisClientFactory;
import dev.kage.server.database.session.RedisConfig;
import dev.kage.server.database.session.RedisSessionAdapter;
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
import dev.kage.server.database.world.PostgresSafeZoneAdapter;
import dev.kage.shop.api.ShopFacade;
import dev.kage.shop.application.BuyItemUseCase;
import dev.kage.shop.application.ListShopItemsUseCase;
import dev.kage.shop.application.SellItemUseCase;
import dev.kage.shop.application.ShopPorts;
import dev.kage.trade.api.TradeFacade;
import dev.kage.trade.application.AcceptTradeRequestUseCase;
import dev.kage.trade.application.CancelTradeUseCase;
import dev.kage.trade.application.ConfirmTradeUseCase;
import dev.kage.trade.application.GetTradeStatusUseCase;
import dev.kage.trade.application.RequestTradeUseCase;
import dev.kage.trade.application.SetTradeOfferUseCase;
import dev.kage.trade.application.TradeOfferChanges;
import dev.kage.trade.application.TradePorts;
import dev.kage.trade.application.TradeUseCases;
import dev.kage.world.api.WorldGeometryFacade;

/**
 * Composition root — nơi DUY NHẤT lắp ráp Facade thật (Adapter + UseCase + TransactionRunner)
 * từ cấu hình môi trường. {@code server-gateway} chỉ gọi {@link #wireAccountFacade()}/{@link
 * #wireCharacterFacade()}, không bao giờ thấy {@link DataSource}/{@link RedisClient} trực tiếp
 * (Điều 23 — server-gateway không biết DB; gọi qua đây không vi phạm vì server-gateway không tự
 * import java.sql/Jedis).
 */
public final class ServerDatabaseBootstrap {

    private final DataSource dataSource;
    private GameEventFacade gameEvents;
    private final RedisClient redisClient;
    private final RetentionJanitor retentionJanitor;

    public ServerDatabaseBootstrap() {
        this(DbMetricsPort.NO_OP);
    }

    /**
     * OBSERVABILITY.md §3.3 {@code kage.db.query.duration}/{@code kage.db.transaction.failed} —
     * {@code metricsPort} cấu hình 1 LẦN vào 2 static utility {@link JdbcOperations}/
     * {@link JdbcTransactionRunner} (KHÔNG thread qua tham số của ~35 {@code Postgres*Adapter}, xem
     * javadoc {@link JdbcOperations#configureMetrics}). Constructor không tham số ở trên (test,
     * hoặc nơi chưa có registry thật) dùng {@link DbMetricsPort#NO_OP} — hành vi y hệt trước khi có
     * metric này.
     */
    public ServerDatabaseBootstrap(DbMetricsPort metricsPort) {
        this(DatabaseConfig.fromEnvironment(), RedisConfig.fromEnvironment(), metricsPort);
    }

    /**
     * Cấu hình TRUYỀN VÀO thay vì đọc biến môi trường — cho test end-to-end lắp ĐÚNG composition root
     * này trên Postgres/Redis của Testcontainers (bộ fuzz opcode gateway+DB, 2026-10-04). Không có nó,
     * test phải chép tay từng {@code wire*Facade} và bản chép lệch dần khỏi bản thật.
     */
    public ServerDatabaseBootstrap(DatabaseConfig databaseConfig, RedisConfig redisConfig, DbMetricsPort metricsPort) {
        this.dataSource = PostgresDataSourceFactory.create(databaseConfig);
        FlywayMigrator.migrate(dataSource);
        this.redisClient = RedisClientFactory.create(redisConfig);
        JdbcOperations.configureMetrics(metricsPort);
        JdbcTransactionRunner.configureMetrics(metricsPort);
        this.retentionJanitor = new RetentionJanitor(dataSource);
        retentionJanitor.start();
    }

    public AccountFacade wireAccountFacade() {
        PostgresAccountAdapter accountPort = new PostgresAccountAdapter();
        RedisSessionAdapter sessionPort = new RedisSessionAdapter(redisClient);
        JdbcAccountTransactionRunner transactionManager = new JdbcAccountTransactionRunner(dataSource);
        LoginOrCreateAccountUseCase useCase =
                new LoginOrCreateAccountUseCase(accountPort, sessionPort, transactionManager);
        return new AccountFacade(useCase);
    }

    public CharacterFacade wireCharacterFacade() {
        PostgresCharacterAdapter characterPort = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        CreateCharacterUseCase createUseCase = new CreateCharacterUseCase(characterPort, transactionRunner);
        ListCharactersUseCase listUseCase = new ListCharactersUseCase(characterPort);
        SelectCharacterUseCase selectUseCase = new SelectCharacterUseCase(characterPort);
        SelectFactionUseCase selectFactionUseCase =
                new SelectFactionUseCase(characterPort, new PostgresFactionChangeAdapter(), transactionRunner);
        CharacterUseCases useCases = new CharacterUseCases(
                createUseCase,
                listUseCase,
                selectUseCase,
                selectFactionUseCase,
                new GrantExperienceUseCase(characterPort));
        return new CharacterFacade(useCases, characterPort);
    }

    public InventoryFacade wireInventoryFacade() {
        PostgresInventoryAdapter inventoryPort = new PostgresInventoryAdapter(dataSource);
        PostgresDropTableAdapter dropTablePort = new PostgresDropTableAdapter(dataSource);
        PostgresItemTemplateAdapter itemTemplatePort = new PostgresItemTemplateAdapter(dataSource);
        PostgresEquipmentAdapter equipmentPort = new PostgresEquipmentAdapter(dataSource);
        PostgresGoldAdapter goldPort = new PostgresGoldAdapter();
        PostgresAuditAdapter auditPort = new PostgresAuditAdapter();
        PostgresInventoryIdempotencyAdapter idempotencyPort = new PostgresInventoryIdempotencyAdapter();
        JdbcInventoryTransactionRunner transactionRunner = new JdbcInventoryTransactionRunner(dataSource);
        AddItemUseCase addItemUseCase = new AddItemUseCase(inventoryPort, itemTemplatePort, transactionRunner);
        GrantLootUseCase grantLootUseCase =
                grantLootUseCase(dropTablePort, inventoryPort, itemTemplatePort, transactionRunner);
        EquipItemUseCase equipItemUseCase =
                new EquipItemUseCase(inventoryPort, itemTemplatePort, equipmentPort, transactionRunner);
        UnequipItemUseCase unequipItemUseCase =
                new UnequipItemUseCase(equipmentPort, transactionRunner).guardedBy(inventoryPort);
        PostgresGemAdapter gemPort = new PostgresGemAdapter(dataSource);
        ComputeEquipmentBonusUseCase computeEquipmentBonusUseCase =
                equipmentBonus(equipmentPort, inventoryPort, itemTemplatePort, gemPort);
        EquipmentUseCases equipmentUseCases =
                new EquipmentUseCases(equipItemUseCase, unequipItemUseCase, computeEquipmentBonusUseCase);
        EnhancePorts enhancePorts = new EnhancePorts(
                inventoryPort,
                goldPort,
                auditPort,
                itemTemplatePort,
                idempotencyPort,
                new PostgresInventoryTradeOfferAdapter());
        EnhanceItemUseCase enhanceItemUseCase = new EnhanceItemUseCase(enhancePorts, transactionRunner);
        UseItemUseCase useItemUseCase = useItemUseCase(inventoryPort, itemTemplatePort, auditPort, transactionRunner);
        InventoryQueryUseCases queryUseCases = new InventoryQueryUseCases(
                new ListInventoryUseCase(inventoryPort, equipmentPort), new GetItemCatalogUseCase(itemTemplatePort));
        return new InventoryFacade(
                new ItemAcquisitionUseCases(addItemUseCase, grantLootUseCase),
                equipmentUseCases,
                new ItemActionUseCases(
                        enhanceItemUseCase,
                        useItemUseCase,
                        storageUseCase(inventoryPort, equipmentPort, itemTemplatePort, transactionRunner),
                        socketGemUseCase(
                                new SocketGemUseCase.Ports(
                                        inventoryPort,
                                        itemTemplatePort,
                                        gemPort,
                                        goldPort,
                                        new PostgresInventoryTradeOfferAdapter()),
                                new SocketGemUseCase.Records(idempotencyPort, auditPort),
                                transactionRunner)),
                queryUseCases);
    }

    /** Dùng vật phẩm (ADR-046 D5) — tách khỏi {@link #wireInventoryFacade} vì trần {@code MethodLength}. */
    private UseItemUseCase useItemUseCase(
            PostgresInventoryAdapter inventoryPort,
            PostgresItemTemplateAdapter itemTemplatePort,
            PostgresAuditAdapter auditPort,
            JdbcInventoryTransactionRunner tx) {
        return new UseItemUseCase(
                new UseItemUseCase.Ports(
                        inventoryPort,
                        itemTemplatePort,
                        auditPort,
                        new PostgresInventoryTradeOfferAdapter(),
                        // ADR-078 — Linh Thú Đan tiến hoá linh thú đang mặc.
                        new PostgresCompanionAdapter(dataSource)),
                tx);
    }

    /** Khảm Linh Ngọc (ADR-064) — tách khỏi {@link #wireInventoryFacade} vì trần {@code MethodLength}. */
    private static SocketGemUseCase socketGemUseCase(
            SocketGemUseCase.Ports ports, SocketGemUseCase.Records records, JdbcInventoryTransactionRunner tx) {
        return new SocketGemUseCase(ports, records, tx, ThreadLocalGemRoll.INSTANCE);
    }

    /** Kho đồ tông môn (ADR-061) — tách khỏi {@link #wireInventoryFacade} vì trần {@code MethodLength}. */
    private StorageUseCase storageUseCase(
            PostgresInventoryAdapter inventoryPort,
            PostgresEquipmentAdapter equipmentPort,
            PostgresItemTemplateAdapter itemTemplatePort,
            JdbcInventoryTransactionRunner tx) {
        return new StorageUseCase(
                new StorageUseCase.Ports(
                        inventoryPort, equipmentPort, itemTemplatePort, new PostgresInventoryTradeOfferAdapter()),
                new PostgresStorageAdapter(dataSource),
                new PostgresAuditAdapter(),
                tx);
    }

    /**
     * Toàn bộ {@code script_ref} quest+dialogue đang được DB tham chiếu — dùng cho validate script
     * lúc khởi động (SCRIPTING.md §8), KHÔNG dùng trong gameplay. Tự dựng adapter riêng (không tái
     * dùng adapter đã wire trong {@link #wireQuestFacade()}/{@link #wireDialogueFacade()}) vì đây
     * là bước validate CHẠY TRƯỚC lúc facade gameplay được cần tới, gọi độc lập từ composition root.
     */
    public List<String> listContentScriptRefs() {
        ArrayList<String> refs = new ArrayList<String>();
        refs.addAll(new PostgresQuestTemplateAdapter(dataSource).listAllScriptRefs());
        refs.addAll(new PostgresNpcTemplateAdapter(dataSource).listAllDialogueScriptRefs());
        // ADR-048 — script AI boss nay là dữ liệu (`monster_template.ai_script_ref`), nên nó phải
        // được duyệt ở ĐÂY. `WorldFacade.listBossScriptResources()` cũ chỉ biết một hằng số.
        refs.addAll(new PostgresMonsterCatalogAdapter(dataSource).listAllBossScriptRefs());
        return refs;
    }

    /**
     * Nối bên nghe "vàng vừa đổi" (ADR-035 Decision-3) — {@code server-gateway} gọi 1 LẦN lúc khởi
     * động để biến mỗi {@link GoldChange} thành {@code GoldChangedEvent} gửi cho đúng người chơi.
     *
     * <p>Đặt ở đây thay vì để gateway gọi thẳng {@code GoldLedger} để giữ nguyên tính chất
     * "composition root chỉ chạm MỘT lớp của server-database" (xem javadoc lớp này). Chưa gọi thì
     * vàng vẫn đổi đúng trong DB, chỉ là client không được báo.
     */
    public void configureGoldChangeListener(GoldChangeListener listener) {
        GoldLedger.configureListener(listener);
    }

    /**
     * Tổng gold TOÀN SERVER (OBSERVABILITY.md §3.5 {@code kage.gold.total} — "metric chống gian
     * lận": tổng tăng đột biến = nghi duplicate). Query trực tiếp thay vì qua 1 feature Facade cụ
     * thể vì đây là số liệu TỔNG HỢP xuyên suốt mọi nhân vật, không thuộc nghiệp vụ riêng của
     * shop/mail/trade/inventory (4 module đó chỉ CHẠM gold của 1 nhân vật/giao dịch, không tổng).
     */
    public long totalGold() {
        return JdbcOperations.queryOne(
                        dataSource,
                        "SELECT COALESCE(SUM(gold), 0) FROM character_stat",
                        resultSet -> resultSet.getLong(1))
                .orElse(0L);
    }

    /** OBSERVABILITY.md §3.3 {@code kage.db.pool.active} — số connection đang được dùng thật. */
    public int poolActiveConnections() {
        return PostgresDataSourceFactory.activeConnections(dataSource);
    }

    /** OBSERVABILITY.md §3.3 {@code kage.db.pool.pending} — số thread đang chờ pool có chỗ trống. */
    public int poolPendingThreads() {
        return PostgresDataSourceFactory.pendingThreads(dataSource);
    }

    // PMD AvoidCatchingGenericException: health check PHẢI trả false cho MỌI lỗi DB (mất kết nối,
    // timeout, driver lỗi...), không riêng SQLException — health/ready không được crash vì lý do đó.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public boolean isReady() {
        try {
            JdbcOperations.queryOne(dataSource, "SELECT 1", resultSet -> resultSet.getInt(1));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public ShopFacade wireShopFacade() {
        PostgresShopCatalogAdapter catalogPort = new PostgresShopCatalogAdapter(dataSource);
        PostgresShopGoldAdapter goldPort = new PostgresShopGoldAdapter();
        PostgresShopInventoryGatewayAdapter inventoryGatewayPort = new PostgresShopInventoryGatewayAdapter(dataSource);
        PostgresShopAuditAdapter auditPort = new PostgresShopAuditAdapter();
        PostgresShopIdempotencyAdapter idempotencyPort = new PostgresShopIdempotencyAdapter();
        JdbcShopTransactionRunner transactionRunner = new JdbcShopTransactionRunner(dataSource);
        ShopPorts ports = new ShopPorts(catalogPort, goldPort, inventoryGatewayPort, auditPort, idempotencyPort);
        ListShopItemsUseCase listShopItemsUseCase = new ListShopItemsUseCase(catalogPort);
        BuyItemUseCase buyItemUseCase = new BuyItemUseCase(ports, transactionRunner);
        SellItemUseCase sellItemUseCase = new SellItemUseCase(ports, transactionRunner);
        return new ShopFacade(listShopItemsUseCase, buyItemUseCase, sellItemUseCase);
    }

    public DialogueFacade wireDialogueFacade() {
        PostgresNpcTemplateAdapter npcTemplatePort = new PostgresNpcTemplateAdapter(dataSource);
        GetDialogueNodeUseCase getDialogueNodeUseCase = new GetDialogueNodeUseCase(npcTemplatePort);
        return new DialogueFacade(getDialogueNodeUseCase);
    }

    /**
     * ADR-040 — {@code npc_spawn}, đọc thuần, nạp một lần lúc khởi động (cùng khuôn
     * {@link #wireMonsterFacade()}). Tách khỏi {@link #wireDialogueFacade()} vì hai facade phục vụ
     * hai thời điểm khác nhau: hội thoại chạy trong lúc chơi, bố trí NPC chỉ đọc lúc dựng world.
     */
    public NpcPlacementFacade wireNpcPlacementFacade() {
        return new NpcPlacementFacade(new PostgresNpcPlacementAdapter(dataSource));
    }

    public QuestFacade wireQuestFacade() {
        PostgresQuestProgressAdapter progressPort = new PostgresQuestProgressAdapter(dataSource);
        PostgresQuestTemplateAdapter templatePort = new PostgresQuestTemplateAdapter(dataSource);
        PostgresQuestExpAdapter expPort = new PostgresQuestExpAdapter();
        PostgresQuestInventoryGrantAdapter inventoryGrantPort = new PostgresQuestInventoryGrantAdapter();
        PostgresQuestAuditAdapter auditPort = new PostgresQuestAuditAdapter();
        PostgresQuestIdempotencyAdapter idempotencyPort = new PostgresQuestIdempotencyAdapter();
        JdbcQuestTransactionRunner transactionRunner = new JdbcQuestTransactionRunner(dataSource);
        AcceptQuestUseCase acceptQuestUseCase = new AcceptQuestUseCase(progressPort, templatePort, transactionRunner);
        ProcessQuestEventUseCase processQuestEventUseCase = new ProcessQuestEventUseCase(
                progressPort, new PostgresQuestItemTakeAdapter(), auditPort, transactionRunner);
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                expPort, inventoryGrantPort, auditPort, idempotencyPort, new PostgresQuestGoldAdapter());
        ClaimQuestRewardUseCase claimQuestRewardUseCase =
                new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, transactionRunner);
        return new QuestFacade(
                acceptQuestUseCase,
                processQuestEventUseCase,
                claimQuestRewardUseCase,
                new dev.kage.quest.api.QuestLogUseCases(
                        new ListQuestsUseCase(progressPort),
                        new AbandonQuestUseCase(progressPort, templatePort, auditPort, transactionRunner)));
    }

    public FriendFacade wireFriendFacade() {
        PostgresFriendAdapter friendPort = new PostgresFriendAdapter(dataSource);
        // Một sổ chờ-sau-từ-chối dùng chung cho gửi và từ chối (dòng 71(b)) — xem FriendRequestCooldowns.
        FriendRequestCooldowns cooldowns = new FriendRequestCooldowns();
        FriendUseCases useCases = new FriendUseCases(
                new SendFriendRequestUseCase(friendPort, cooldowns),
                new AcceptFriendRequestUseCase(friendPort),
                new RejectFriendRequestUseCase(friendPort, cooldowns),
                new RemoveFriendUseCase(friendPort),
                new ListFriendsUseCase(friendPort),
                new ListPendingFriendRequestsUseCase(friendPort));
        return new FriendFacade(useCases);
    }

    public MailFacade wireMailFacade() {
        PostgresMailRepositoryAdapter repositoryPort = new PostgresMailRepositoryAdapter(dataSource);
        PostgresMailGoldAdapter goldPort = new PostgresMailGoldAdapter();
        PostgresMailInventoryGatewayAdapter inventoryGatewayPort = new PostgresMailInventoryGatewayAdapter();
        PostgresMailAuditAdapter auditPort = new PostgresMailAuditAdapter();
        PostgresMailIdempotencyAdapter idempotencyPort = new PostgresMailIdempotencyAdapter();
        JdbcMailTransactionRunner transactionRunner = new JdbcMailTransactionRunner(dataSource);
        MailPorts ports = new MailPorts(repositoryPort, goldPort, inventoryGatewayPort, auditPort, idempotencyPort);
        SendMailUseCase sendMailUseCase = new SendMailUseCase(ports, transactionRunner);
        ListMailUseCase listMailUseCase = new ListMailUseCase(repositoryPort);
        ClaimMailUseCase claimMailUseCase = new ClaimMailUseCase(ports, transactionRunner);
        DeleteMailUseCase deleteMailUseCase = new DeleteMailUseCase(repositoryPort);
        return new MailFacade(sendMailUseCase, listMailUseCase, claimMailUseCase, deleteMailUseCase);
    }

    public GuildFacade wireGuildFacade() {
        PostgresGuildAdapter guildPort = new PostgresGuildAdapter(dataSource);
        GuildUseCases useCases = new GuildUseCases(
                new CreateGuildUseCase(guildPort),
                new SendGuildJoinRequestUseCase(guildPort),
                new ApproveGuildJoinRequestUseCase(guildPort),
                new RejectGuildJoinRequestUseCase(guildPort),
                new LeaveGuildUseCase(guildPort),
                new DisbandGuildUseCase(guildPort),
                new KickGuildMemberUseCase(guildPort),
                new ListGuildMembersUseCase(guildPort),
                new ListPendingGuildJoinRequestsUseCase(guildPort),
                new FindMyGuildUseCase(guildPort));
        return new GuildFacade(useCases);
    }

    public PartyFacade wirePartyFacade() {
        PostgresPartyAdapter partyPort = new PostgresPartyAdapter(dataSource);
        PartyUseCases useCases = new PartyUseCases(
                new CreatePartyUseCase(partyPort),
                new InvitePartyMemberUseCase(partyPort),
                new AcceptPartyInviteUseCase(partyPort),
                new RejectPartyInviteUseCase(partyPort),
                new LeavePartyUseCase(partyPort),
                new KickPartyMemberUseCase(partyPort),
                new ListPartyMembersUseCase(partyPort),
                new ListPendingPartyInvitesUseCase(partyPort),
                new FindMyPartyUseCase(partyPort),
                new dev.kage.party.application.HandOverPartyLeadershipUseCase(partyPort));
        return new PartyFacade(useCases);
    }

    public PvpFacade wirePvpFacade() {
        return new PvpFacade(new PostgresPvpRatingAdapter(dataSource));
    }

    /** ADR-037 — {@code monster_template}/{@code monster_spawn}, đọc thuần, nạp một lần lúc khởi động. */
    public MonsterFacade wireMonsterFacade() {
        return new MonsterFacade(new PostgresMonsterCatalogAdapter(dataSource));
    }

    /** ADR-046 D3 — {@code safe_zone}, đọc thuần, nạp một lần lúc khởi động (cùng khuôn quái/NPC). */
    public WorldGeometryFacade wireWorldGeometryFacade() {
        return new WorldGeometryFacade(new PostgresSafeZoneAdapter(dataSource));
    }

    public TradeFacade wireTradeFacade() {
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        PostgresTradeInventoryAdapter inventoryPort = new PostgresTradeInventoryAdapter(dataSource);
        PostgresTradeGoldAdapter goldPort = new PostgresTradeGoldAdapter();
        PostgresTradeAuditAdapter auditPort = new PostgresTradeAuditAdapter();
        PostgresTradeIdempotencyAdapter idempotencyPort = new PostgresTradeIdempotencyAdapter();
        JdbcTradeTransactionRunner transactionRunner = new JdbcTradeTransactionRunner(dataSource);
        TradePorts ports = new TradePorts(sessionPort, inventoryPort, goldPort, auditPort, idempotencyPort);
        // Một sổ "lời chào vừa đổi" dùng chung cho sửa lời chào + xác nhận (dòng 25(i)(b)).
        TradeOfferChanges offerChanges = new TradeOfferChanges();
        TradeUseCases useCases = new TradeUseCases(
                new RequestTradeUseCase(sessionPort),
                new AcceptTradeRequestUseCase(sessionPort),
                new CancelTradeUseCase(sessionPort),
                new SetTradeOfferUseCase(sessionPort, offerChanges),
                new ConfirmTradeUseCase(ports, transactionRunner, offerChanges),
                new GetTradeStatusUseCase(sessionPort, inventoryPort));
        return new TradeFacade(useCases);
    }

    public ChatFacade wireChatFacade() {
        PostgresChatAuditAdapter auditPort = new PostgresChatAuditAdapter(dataSource);
        return new ChatFacade(new SendChatMessageUseCase(auditPort), new PostgresChatMuteAdapter(dataSource));
    }

    /** ADR-071 — điểm công pháp (bậc chiêu). */
    public SkillRankFacade wireSkillRankFacade() {
        return new SkillRankFacade(new SkillRankUseCase(
                new PostgresSkillRankAdapter(dataSource), new JdbcCharacterTransactionRunner(dataSource)));
    }

    /** ADR-068/069 — cấp bang, cống hiến, nhiệm vụ tuần. */
    public GuildProgressionFacade wireGuildProgressionFacade() {
        return new GuildProgressionFacade(
                new GuildProgressionUseCase(new PostgresGuildProgressionAdapter(dataSource), Clock.systemUTC()));
    }

    /** Chỉ số trang bị kèm Linh Ngọc, bộ pháp khí (ADR-065), phi kiếm + linh thú (ADR-070/072). */
    private ComputeEquipmentBonusUseCase equipmentBonus(
            PostgresEquipmentAdapter equipmentPort,
            PostgresInventoryAdapter inventoryPort,
            PostgresItemTemplateAdapter itemTemplatePort,
            PostgresGemAdapter gemPort) {
        return new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort, gemPort)
                .withItemSets(new PostgresItemSetAdapter(dataSource))
                .withCompanions(new PostgresCompanionAdapter(dataSource));
    }

    /** Rơi đồ có audit (2026-10-11) và số lượt roll theo sự kiện nhân đôi (ADR-067). */
    private GrantLootUseCase grantLootUseCase(
            PostgresDropTableAdapter dropTablePort,
            PostgresInventoryAdapter inventoryPort,
            PostgresItemTemplateAdapter itemTemplatePort,
            JdbcInventoryTransactionRunner transactionRunner) {
        return new GrantLootUseCase(dropTablePort, inventoryPort, itemTemplatePort, transactionRunner)
                .auditedBy(new PostgresAuditAdapter())
                .withRollCount(() -> wireGameEventFacade().multiplier(GameEventKind.DROP));
    }

    /**
     * ADR-067 — sự kiện nhân đôi. MỘT bản cho cả tiến trình (rơi đồ ở đây và EXP/lệnh GM ở gateway cùng đọc
     * một trạng thái) — tạo lần đầu được hỏi, đọc DB lúc đó.
     */
    public synchronized GameEventFacade wireGameEventFacade() {
        if (gameEvents == null) {
            gameEvents = new GameEventFacade(new PostgresGameEventAdapter(dataSource), Clock.systemUTC());
        }
        return gameEvents;
    }

    /** ADR-052 — quyền GM (chỉ đọc) + sổ audit write-ahead. */
    public GmFacade wireGmFacade() {
        return new GmFacade(new PostgresGmRoleAdapter(dataSource), new PostgresGmAuditAdapter(dataSource));
    }

    /** Phường thị (ADR-063) — mọi port trong CÙNG transaction (túi đồ, vàng, dòng treo, khoá, audit). */
    public MarketFacade wireMarketFacade() {
        return new MarketFacade(new MarketUseCase(
                new MarketUseCase.Ports(
                        new PostgresMarketAdapter(dataSource),
                        new PostgresMarketInventoryAdapter(),
                        new PostgresMarketGoldAdapter(),
                        new PostgresMarketRecordAdapter()),
                new JdbcMarketTransactionRunner(dataSource),
                java.time.Clock.systemUTC()));
    }

    /** Thành tựu + danh hiệu (ADR-062). */
    public AchievementFacade wireAchievementFacade() {
        return new AchievementFacade(new AchievementUseCase(new PostgresAchievementAdapter(dataSource)));
    }

    /**
     * Điểm danh hằng ngày (ADR-060). {@code sender} do gateway dựng (thư hệ thống) — feature-checkin không
     * biết feature thư.
     */
    public CheckInFacade wireCheckInFacade(CheckInRewardSender sender) {
        return new CheckInFacade(new ClaimDailyCheckInUseCase(
                new PostgresCheckInAdapter(dataSource), sender, java.time.Clock.systemUTC()));
    }

    /** Cửa hàng bang (ADR-080). */
    public GuildShopFacade wireGuildShopFacade() {
        return new GuildShopFacade(new PostgresGuildShopAdapter(dataSource));
    }

    /** Luyện đan (ADR-079) — công thức đọc một lần, mỗi mẻ một transaction. */
    public CraftingFacade wireCraftingFacade() {
        return new CraftingFacade(new CraftItemUseCase(
                new PostgresRecipeAdapter(dataSource),
                new PostgresCraftingWorkshopAdapter(),
                new JdbcCraftingTransactionRunner(dataSource)));
    }

    /** Đóng DB/Redis (SERVER.md §10 graceful shutdown, bước 5) — gọi sau khi tick/flush xong. */
    public void shutdown() {
        retentionJanitor.stop();
        redisClient.close();
        PostgresDataSourceFactory.close(dataSource);
    }
}
