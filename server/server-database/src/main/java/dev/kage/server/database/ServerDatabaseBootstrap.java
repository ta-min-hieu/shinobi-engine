package dev.kage.server.database;

import dev.kage.account.api.AccountFacade;
import dev.kage.account.application.LoginOrCreateAccountUseCase;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.chat.api.ChatFacade;
import dev.kage.chat.application.SendChatMessageUseCase;
import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.dialogue.application.GetDialogueNodeUseCase;
import dev.kage.friend.api.FriendFacade;
import dev.kage.friend.application.AcceptFriendRequestUseCase;
import dev.kage.friend.application.FriendUseCases;
import dev.kage.friend.application.ListFriendsUseCase;
import dev.kage.friend.application.ListPendingFriendRequestsUseCase;
import dev.kage.friend.application.RejectFriendRequestUseCase;
import dev.kage.friend.application.RemoveFriendUseCase;
import dev.kage.friend.application.SendFriendRequestUseCase;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.application.ApproveGuildJoinRequestUseCase;
import dev.kage.guild.application.CreateGuildUseCase;
import dev.kage.guild.application.DisbandGuildUseCase;
import dev.kage.guild.application.FindMyGuildUseCase;
import dev.kage.guild.application.GuildUseCases;
import dev.kage.guild.application.KickGuildMemberUseCase;
import dev.kage.guild.application.LeaveGuildUseCase;
import dev.kage.guild.application.ListGuildMembersUseCase;
import dev.kage.guild.application.ListPendingGuildJoinRequestsUseCase;
import dev.kage.guild.application.RejectGuildJoinRequestUseCase;
import dev.kage.guild.application.SendGuildJoinRequestUseCase;
import dev.kage.inventory.api.EquipmentUseCases;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EnhancePorts;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.application.UnequipItemUseCase;
import dev.kage.mail.api.MailFacade;
import dev.kage.mail.application.ClaimMailUseCase;
import dev.kage.mail.application.DeleteMailUseCase;
import dev.kage.mail.application.ListMailUseCase;
import dev.kage.mail.application.MailPorts;
import dev.kage.mail.application.SendMailUseCase;
import dev.kage.quest.api.QuestFacade;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ProcessMonsterKilledUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.chat.PostgresChatAuditAdapter;
import dev.kage.server.database.dialogue.PostgresNpcTemplateAdapter;
import dev.kage.server.database.friend.PostgresFriendAdapter;
import dev.kage.server.database.guild.PostgresGuildAdapter;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresAuditAdapter;
import dev.kage.server.database.inventory.PostgresDropTableAdapter;
import dev.kage.server.database.inventory.PostgresEquipmentAdapter;
import dev.kage.server.database.inventory.PostgresGoldAdapter;
import dev.kage.server.database.inventory.PostgresInventoryAdapter;
import dev.kage.server.database.inventory.PostgresItemTemplateAdapter;
import dev.kage.server.database.mail.JdbcMailTransactionRunner;
import dev.kage.server.database.mail.PostgresMailAuditAdapter;
import dev.kage.server.database.mail.PostgresMailGoldAdapter;
import dev.kage.server.database.mail.PostgresMailInventoryGatewayAdapter;
import dev.kage.server.database.mail.PostgresMailRepositoryAdapter;
import dev.kage.server.database.quest.JdbcQuestTransactionRunner;
import dev.kage.server.database.quest.PostgresQuestAuditAdapter;
import dev.kage.server.database.quest.PostgresQuestExpAdapter;
import dev.kage.server.database.quest.PostgresQuestInventoryGrantAdapter;
import dev.kage.server.database.quest.PostgresQuestProgressAdapter;
import dev.kage.server.database.quest.PostgresQuestTemplateAdapter;
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
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Toàn bộ {@code script_ref} quest+dialogue đang được DB tham chiếu — dùng cho validate script
     * lúc khởi động (SCRIPTING.md §8), KHÔNG dùng trong gameplay. Tự dựng adapter riêng (không tái
     * dùng adapter đã wire trong {@link #wireQuestFacade()}/{@link #wireDialogueFacade()}) vì đây
     * là bước validate CHẠY TRƯỚC lúc facade gameplay được cần tới, gọi độc lập từ composition root.
     */
    public List<String> listContentScriptRefs() {
        var refs = new ArrayList<String>();
        refs.addAll(new PostgresQuestTemplateAdapter(dataSource).listAllScriptRefs());
        refs.addAll(new PostgresNpcTemplateAdapter(dataSource).listAllDialogueScriptRefs());
        return refs;
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

    public QuestFacade wireQuestFacade() {
        var progressPort = new PostgresQuestProgressAdapter(dataSource);
        var templatePort = new PostgresQuestTemplateAdapter(dataSource);
        var expPort = new PostgresQuestExpAdapter();
        var inventoryGrantPort = new PostgresQuestInventoryGrantAdapter(dataSource);
        var auditPort = new PostgresQuestAuditAdapter();
        var transactionRunner = new JdbcQuestTransactionRunner(dataSource);
        var acceptQuestUseCase = new AcceptQuestUseCase(progressPort, templatePort, transactionRunner);
        var processMonsterKilledUseCase =
                new ProcessMonsterKilledUseCase(progressPort, templatePort, transactionRunner);
        var rewardPorts = new QuestRewardPorts(expPort, inventoryGrantPort, auditPort);
        var claimQuestRewardUseCase =
                new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, transactionRunner);
        return new QuestFacade(acceptQuestUseCase, processMonsterKilledUseCase, claimQuestRewardUseCase);
    }

    public FriendFacade wireFriendFacade() {
        var friendPort = new PostgresFriendAdapter(dataSource);
        var useCases = new FriendUseCases(
                new SendFriendRequestUseCase(friendPort),
                new AcceptFriendRequestUseCase(friendPort),
                new RejectFriendRequestUseCase(friendPort),
                new RemoveFriendUseCase(friendPort),
                new ListFriendsUseCase(friendPort),
                new ListPendingFriendRequestsUseCase(friendPort));
        return new FriendFacade(useCases);
    }

    public MailFacade wireMailFacade() {
        var repositoryPort = new PostgresMailRepositoryAdapter(dataSource);
        var goldPort = new PostgresMailGoldAdapter();
        var inventoryGatewayPort = new PostgresMailInventoryGatewayAdapter(dataSource);
        var auditPort = new PostgresMailAuditAdapter();
        var transactionRunner = new JdbcMailTransactionRunner(dataSource);
        var sendMailUseCase = new SendMailUseCase(repositoryPort, transactionRunner);
        var listMailUseCase = new ListMailUseCase(repositoryPort);
        var ports = new MailPorts(repositoryPort, goldPort, inventoryGatewayPort, auditPort);
        var claimMailUseCase = new ClaimMailUseCase(ports, transactionRunner);
        var deleteMailUseCase = new DeleteMailUseCase(repositoryPort);
        return new MailFacade(sendMailUseCase, listMailUseCase, claimMailUseCase, deleteMailUseCase);
    }

    public GuildFacade wireGuildFacade() {
        var guildPort = new PostgresGuildAdapter(dataSource);
        var useCases = new GuildUseCases(
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

    public ChatFacade wireChatFacade() {
        var auditPort = new PostgresChatAuditAdapter(dataSource);
        return new ChatFacade(new SendChatMessageUseCase(auditPort));
    }

    /** Đóng DB/Redis (SERVER.md §10 graceful shutdown, bước 5) — gọi sau khi tick/flush xong. */
    public void shutdown() {
        jedisPool.close();
        PostgresDataSourceFactory.close(dataSource);
    }
}
