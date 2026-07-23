package dev.kage.server.database;

import dev.kage.account.api.AccountFacade;
import dev.kage.account.application.LoginOrCreateAccountUseCase;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.dialogue.application.GetDialogueNodeUseCase;
import dev.kage.inventory.api.EquipmentUseCases;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EnhancePorts;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.application.UnequipItemUseCase;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.dialogue.PostgresNpcTemplateAdapter;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresAuditAdapter;
import dev.kage.server.database.inventory.PostgresDropTableAdapter;
import dev.kage.server.database.inventory.PostgresEquipmentAdapter;
import dev.kage.server.database.inventory.PostgresGoldAdapter;
import dev.kage.server.database.inventory.PostgresInventoryAdapter;
import dev.kage.server.database.inventory.PostgresItemTemplateAdapter;
import dev.kage.server.database.session.RedisClientFactory;
import dev.kage.server.database.session.RedisConfig;
import dev.kage.server.database.session.RedisSessionAdapter;
import dev.kage.server.database.shop.JdbcShopTransactionRunner;
import dev.kage.server.database.shop.PostgresShopAuditAdapter;
import dev.kage.server.database.shop.PostgresShopCatalogAdapter;
import dev.kage.server.database.shop.PostgresShopGoldAdapter;
import dev.kage.server.database.shop.PostgresShopInventoryGatewayAdapter;
import dev.kage.shop.api.ShopFacade;
import dev.kage.shop.application.BuyItemUseCase;
import dev.kage.shop.application.ListShopItemsUseCase;
import dev.kage.shop.application.SellItemUseCase;
import dev.kage.shop.application.ShopPorts;
import javax.sql.DataSource;
import redis.clients.jedis.JedisPool;

/**
 * Composition root — nơi DUY NHẤT lắp ráp Facade thật (Adapter + UseCase + TransactionRunner)
 * từ cấu hình môi trường. {@code server-gateway} chỉ gọi {@link #wireAccountFacade()}/{@link
 * #wireCharacterFacade()}, không bao giờ thấy {@link DataSource}/{@link JedisPool} trực tiếp
 * (Điều 23 — server-gateway không biết DB; gọi qua đây không vi phạm vì server-gateway không tự
 * import java.sql/Jedis).
 */
public final class ServerDatabaseBootstrap {

    private final DataSource dataSource;
    private final JedisPool jedisPool;

    public ServerDatabaseBootstrap() {
        this.dataSource = PostgresDataSourceFactory.create(DatabaseConfig.fromEnvironment());
        FlywayMigrator.migrate(dataSource);
        this.jedisPool = RedisClientFactory.create(RedisConfig.fromEnvironment());
    }

    public AccountFacade wireAccountFacade() {
        var accountPort = new PostgresAccountAdapter();
        var sessionPort = new RedisSessionAdapter(jedisPool);
        var transactionManager = new JdbcAccountTransactionRunner(dataSource);
        var useCase = new LoginOrCreateAccountUseCase(accountPort, sessionPort, transactionManager);
        return new AccountFacade(useCase);
    }

    public CharacterFacade wireCharacterFacade() {
        var characterPort = new PostgresCharacterAdapter(dataSource);
        var transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        var createUseCase = new CreateCharacterUseCase(characterPort, transactionRunner);
        var listUseCase = new ListCharactersUseCase(characterPort);
        var selectUseCase = new SelectCharacterUseCase(characterPort);
        return new CharacterFacade(createUseCase, listUseCase, selectUseCase, characterPort);
    }

    public InventoryFacade wireInventoryFacade() {
        var inventoryPort = new PostgresInventoryAdapter(dataSource);
        var dropTablePort = new PostgresDropTableAdapter(dataSource);
        var itemTemplatePort = new PostgresItemTemplateAdapter(dataSource);
        var equipmentPort = new PostgresEquipmentAdapter(dataSource);
        var goldPort = new PostgresGoldAdapter();
        var auditPort = new PostgresAuditAdapter();
        var transactionRunner = new JdbcInventoryTransactionRunner(dataSource);
        var addItemUseCase = new AddItemUseCase(inventoryPort, transactionRunner);
        var grantLootUseCase = new GrantLootUseCase(dropTablePort, inventoryPort, transactionRunner);
        var equipItemUseCase =
                new EquipItemUseCase(inventoryPort, itemTemplatePort, equipmentPort, transactionRunner);
        var unequipItemUseCase = new UnequipItemUseCase(equipmentPort, transactionRunner);
        var computeEquipmentBonusUseCase =
                new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort);
        var equipmentUseCases =
                new EquipmentUseCases(equipItemUseCase, unequipItemUseCase, computeEquipmentBonusUseCase);
        var enhancePorts = new EnhancePorts(inventoryPort, goldPort, auditPort, itemTemplatePort);
        var enhanceItemUseCase = new EnhanceItemUseCase(enhancePorts, transactionRunner);
        return new InventoryFacade(addItemUseCase, grantLootUseCase, equipmentUseCases, enhanceItemUseCase);
    }

    public ShopFacade wireShopFacade() {
        var catalogPort = new PostgresShopCatalogAdapter(dataSource);
        var goldPort = new PostgresShopGoldAdapter();
        var inventoryGatewayPort = new PostgresShopInventoryGatewayAdapter(dataSource);
        var auditPort = new PostgresShopAuditAdapter();
        var transactionRunner = new JdbcShopTransactionRunner(dataSource);
        var ports = new ShopPorts(catalogPort, goldPort, inventoryGatewayPort, auditPort);
        var listShopItemsUseCase = new ListShopItemsUseCase(catalogPort);
        var buyItemUseCase = new BuyItemUseCase(ports, transactionRunner);
        var sellItemUseCase = new SellItemUseCase(ports, transactionRunner);
        return new ShopFacade(listShopItemsUseCase, buyItemUseCase, sellItemUseCase);
    }

    public DialogueFacade wireDialogueFacade() {
        var npcTemplatePort = new PostgresNpcTemplateAdapter(dataSource);
        var getDialogueNodeUseCase = new GetDialogueNodeUseCase(npcTemplatePort);
        return new DialogueFacade(getDialogueNodeUseCase);
    }

    /** Đóng DB/Redis (SERVER.md §10 graceful shutdown, bước 5) — gọi sau khi tick/flush xong. */
    public void shutdown() {
        jedisPool.close();
        PostgresDataSourceFactory.close(dataSource);
    }
}
