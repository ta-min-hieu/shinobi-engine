package dev.kage.server.database.economy;

import javax.sql.DataSource;

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
import dev.kage.inventory.application.UnequipItemUseCase;
import dev.kage.inventory.application.UseItemUseCase;
import dev.kage.mail.api.MailFacade;
import dev.kage.mail.application.ClaimMailUseCase;
import dev.kage.mail.application.DeleteMailUseCase;
import dev.kage.mail.application.ListMailUseCase;
import dev.kage.mail.application.MailPorts;
import dev.kage.mail.application.SendMailUseCase;
import dev.kage.quest.api.QuestFacade;
import dev.kage.quest.application.AbandonQuestUseCase;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ListQuestsUseCase;
import dev.kage.quest.application.ProcessQuestEventUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresAuditAdapter;
import dev.kage.server.database.inventory.PostgresDropTableAdapter;
import dev.kage.server.database.inventory.PostgresEquipmentAdapter;
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
import dev.kage.trade.application.TradePorts;
import dev.kage.trade.application.TradeUseCases;

/**
 * Năm Facade kinh tế lắp y hệt {@code ServerDatabaseBootstrap#wireXxxFacade} — đúng những đối tượng
 * {@code GatewayChannelHandler}/{@code ConnectedCharacterRegistry} gọi trong production. Không dùng
 * thẳng {@code ServerDatabaseBootstrap} vì constructor của nó đọc biến môi trường và mở Redis.
 *
 * <p>⚠️ Bản chép tay: đổi cách lắp ở {@code ServerDatabaseBootstrap} thì sửa cả ở đây. MỘT
 * {@link InventoryFacade} dùng chung cho mọi luồng — khoá JVM theo nhân vật của nó là một phần hành
 * vi production (một tiến trình gateway, một facade).
 */
record EconomyStressFacades(
        InventoryFacade inventory, ShopFacade shop, MailFacade mail, TradeFacade trade, QuestFacade quest) {

    static EconomyStressFacades wire(DataSource dataSource) {
        return new EconomyStressFacades(
                wireInventory(dataSource),
                wireShop(dataSource),
                wireMail(dataSource),
                wireTrade(dataSource),
                wireQuest(dataSource));
    }

    private static InventoryFacade wireInventory(DataSource dataSource) {
        PostgresInventoryAdapter inventoryPort = new PostgresInventoryAdapter(dataSource);
        PostgresItemTemplateAdapter itemTemplatePort = new PostgresItemTemplateAdapter(dataSource);
        PostgresEquipmentAdapter equipmentPort = new PostgresEquipmentAdapter(dataSource);
        PostgresAuditAdapter auditPort = new PostgresAuditAdapter();
        JdbcInventoryTransactionRunner transactionRunner = new JdbcInventoryTransactionRunner(dataSource);
        AddItemUseCase addItemUseCase = new AddItemUseCase(inventoryPort, itemTemplatePort, transactionRunner);
        GrantLootUseCase grantLootUseCase = new GrantLootUseCase(
                new PostgresDropTableAdapter(dataSource), inventoryPort, itemTemplatePort, transactionRunner);
        EquipmentUseCases equipmentUseCases = new EquipmentUseCases(
                new EquipItemUseCase(inventoryPort, itemTemplatePort, equipmentPort, transactionRunner),
                new UnequipItemUseCase(equipmentPort, transactionRunner),
                new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort));
        EnhancePorts enhancePorts = new EnhancePorts(
                inventoryPort,
                new PostgresGoldAdapter(),
                auditPort,
                itemTemplatePort,
                new PostgresInventoryIdempotencyAdapter(),
                new PostgresInventoryTradeOfferAdapter());
        ItemActionUseCases actionUseCases = new ItemActionUseCases(
                new EnhanceItemUseCase(enhancePorts, transactionRunner),
                new UseItemUseCase(
                        new UseItemUseCase.Ports(
                                inventoryPort, itemTemplatePort, auditPort, new PostgresInventoryTradeOfferAdapter()),
                        transactionRunner));
        InventoryQueryUseCases queryUseCases = new InventoryQueryUseCases(
                new ListInventoryUseCase(inventoryPort, equipmentPort), new GetItemCatalogUseCase(itemTemplatePort));
        return new InventoryFacade(
                new ItemAcquisitionUseCases(addItemUseCase, grantLootUseCase),
                equipmentUseCases,
                actionUseCases,
                queryUseCases);
    }

    private static ShopFacade wireShop(DataSource dataSource) {
        PostgresShopCatalogAdapter catalogPort = new PostgresShopCatalogAdapter(dataSource);
        ShopPorts ports = new ShopPorts(
                catalogPort,
                new PostgresShopGoldAdapter(),
                new PostgresShopInventoryGatewayAdapter(dataSource),
                new PostgresShopAuditAdapter(),
                new PostgresShopIdempotencyAdapter());
        JdbcShopTransactionRunner transactionRunner = new JdbcShopTransactionRunner(dataSource);
        return new ShopFacade(
                new ListShopItemsUseCase(catalogPort),
                new BuyItemUseCase(ports, transactionRunner),
                new SellItemUseCase(ports, transactionRunner));
    }

    private static MailFacade wireMail(DataSource dataSource) {
        PostgresMailRepositoryAdapter repositoryPort = new PostgresMailRepositoryAdapter(dataSource);
        MailPorts ports = new MailPorts(
                repositoryPort,
                new PostgresMailGoldAdapter(),
                new PostgresMailInventoryGatewayAdapter(),
                new PostgresMailAuditAdapter(),
                new PostgresMailIdempotencyAdapter());
        JdbcMailTransactionRunner transactionRunner = new JdbcMailTransactionRunner(dataSource);
        return new MailFacade(
                new SendMailUseCase(ports, transactionRunner),
                new ListMailUseCase(repositoryPort),
                new ClaimMailUseCase(ports, transactionRunner),
                new DeleteMailUseCase(repositoryPort));
    }

    private static TradeFacade wireTrade(DataSource dataSource) {
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        PostgresTradeInventoryAdapter inventoryPort = new PostgresTradeInventoryAdapter(dataSource);
        TradePorts ports = new TradePorts(
                sessionPort,
                inventoryPort,
                new PostgresTradeGoldAdapter(),
                new PostgresTradeAuditAdapter(),
                new PostgresTradeIdempotencyAdapter());
        return new TradeFacade(new TradeUseCases(
                new RequestTradeUseCase(sessionPort),
                new AcceptTradeRequestUseCase(sessionPort),
                new CancelTradeUseCase(sessionPort),
                new SetTradeOfferUseCase(sessionPort),
                new ConfirmTradeUseCase(ports, new JdbcTradeTransactionRunner(dataSource)),
                new GetTradeStatusUseCase(sessionPort, inventoryPort)));
    }

    private static QuestFacade wireQuest(DataSource dataSource) {
        PostgresQuestProgressAdapter progressPort = new PostgresQuestProgressAdapter(dataSource);
        PostgresQuestTemplateAdapter templatePort = new PostgresQuestTemplateAdapter(dataSource);
        PostgresQuestAuditAdapter auditPort = new PostgresQuestAuditAdapter();
        JdbcQuestTransactionRunner transactionRunner = new JdbcQuestTransactionRunner(dataSource);
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(),
                new PostgresQuestInventoryGrantAdapter(),
                auditPort,
                new PostgresQuestIdempotencyAdapter());
        return new QuestFacade(
                new AcceptQuestUseCase(progressPort, templatePort, transactionRunner),
                new ProcessQuestEventUseCase(
                        progressPort, new PostgresQuestItemTakeAdapter(), auditPort, transactionRunner),
                new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, transactionRunner),
                new dev.kage.quest.api.QuestLogUseCases(
                        new ListQuestsUseCase(progressPort),
                        new AbandonQuestUseCase(progressPort, templatePort, auditPort, transactionRunner)));
    }
}
