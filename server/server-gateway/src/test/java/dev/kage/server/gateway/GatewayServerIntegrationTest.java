package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.account.api.AccountFacade;
import dev.kage.account.application.LoginOrCreateAccountUseCase;
import dev.kage.account.domain.Account;
import dev.kage.account.domain.AccountPort;
import dev.kage.account.domain.SessionPort;
import dev.kage.account.domain.Transaction;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.inventory.api.EquipmentUseCases;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EnhancePorts;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.application.UnequipItemUseCase;
import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.DropTableEntry;
import dev.kage.inventory.domain.DropTablePort;
import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.GoldPort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.BuyItemCommand;
import dev.kage.protocol.BuyItemResponse;
import dev.kage.protocol.BuyOutcome;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.ClaimQuestRewardCommand;
import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.EnhanceItemCommand;
import dev.kage.protocol.EnhanceItemResponse;
import dev.kage.protocol.EnhanceOutcome;
import dev.kage.protocol.EnterDungeonCommand;
import dev.kage.protocol.EnterDungeonOutcome;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.ExitDungeonCommand;
import dev.kage.protocol.ExitDungeonOutcome;
import dev.kage.protocol.ExitDungeonResponse;
import dev.kage.protocol.EquipItemCommand;
import dev.kage.protocol.EquipItemResponse;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ListShopItemsRequest;
import dev.kage.protocol.ListShopItemsResponse;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SellItemCommand;
import dev.kage.protocol.SellItemResponse;
import dev.kage.protocol.SellOutcome;
import dev.kage.protocol.SendFriendRequestCommand;
import dev.kage.protocol.SendFriendRequestOutcome;
import dev.kage.protocol.SendFriendRequestResponse;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageOutcome;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.SendMailCommand;
import dev.kage.protocol.SendMailOutcome;
import dev.kage.protocol.SendMailResponse;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.TalkToNpcCommand;
import dev.kage.protocol.UnequipItemCommand;
import dev.kage.protocol.UnequipItemResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.world.WorldFacade;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Test end-to-end bằng SOCKET TCP THẬT (không {@code EmbeddedChannel}) — verify đúng ROADMAP
 * Phase 2 Exit Criteria "Client kết nối server, ping/pong hoạt động" qua chính giao thức dây,
 * không chỉ pipeline nội bộ.
 *
 * <p>Dùng {@code java.net.Socket} thuần (không {@code GameConnection} của client-core) để tránh
 * server-gateway phụ thuộc ngược vào client — module server tự đủ để test chính nó.
 */
class GatewayServerIntegrationTest {

    private GatewayServer server;
    private WorldFacade worldFacade;
    private CharacterPort characterPort;
    private ConnectedCharacterRegistry characterRegistry;
    private dev.kage.party.api.PartyFacade partyFacade;

    @BeforeEach
    void startServer() throws InterruptedException {
        // port 0 = OS tự cấp cổng trống — tránh đụng cổng khi nhiều test chạy song song.
        // AccountFacade/CharacterFacade THẬT với Fake Port (không server-database thật) — test
        // này chỉ verify ping/pong qua socket thật, không chạm Connect/Character, nhưng
        // GatewayServer luôn cần cả 2 Facade để khởi tạo (xem GatewayServer).
        CharacterFacade characterFacade = fakeCharacterFacade();
        InventoryFacade inventoryFacade = fakeInventoryFacade();
        dev.kage.shop.api.ShopFacade shopFacade = fakeShopFacade();
        dev.kage.dialogue.api.DialogueFacade dialogueFacade = fakeDialogueFacade();
        dev.kage.quest.api.QuestFacade questFacade = fakeQuestFacade();
        dev.kage.friend.api.FriendFacade friendFacade = fakeFriendFacade();
        dev.kage.mail.api.MailFacade mailFacade = fakeMailFacade();
        dev.kage.chat.api.ChatFacade chatFacade = fakeChatFacade();
        dev.kage.guild.api.GuildFacade guildFacade = fakeGuildFacade();
        partyFacade = fakePartyFacade();
        dev.kage.trade.api.TradeFacade tradeFacade = fakeTradeFacade();
        characterRegistry = new ConnectedCharacterRegistry(inventoryFacade, questFacade);
        worldFacade = new WorldFacade(characterRegistry);
        worldFacade.start();
        CharacterPositionPersister positionPersister =
                new CharacterPositionPersister(characterFacade, characterRegistry, Duration.ofMinutes(5));
        DungeonInstanceRegistry dungeonInstanceRegistry =
                new DungeonInstanceRegistry(characterRegistry, characterFacade, partyFacade, worldFacade);
        WorldGateway worldGateway =
                new WorldGateway(worldFacade, characterRegistry, positionPersister, dungeonInstanceRegistry);
        SocialFacades socialFacades =
                new SocialFacades(friendFacade, mailFacade, chatFacade, guildFacade, partyFacade, tradeFacade);
        ContentFacades contentFacades = new ContentFacades(shopFacade, dialogueFacade, questFacade, socialFacades);
        GatewayFacades facades =
                new GatewayFacades(fakeAccountFacade(), characterFacade, inventoryFacade, contentFacades);
        server = new GatewayServer(0, facades, worldGateway);
        server.start();
    }

    private static AccountFacade fakeAccountFacade() {
        AccountPort accountPort = new AccountPort() {
            private final Map<String, Account> byUsername = new HashMap<>();
            private final AtomicLong nextId = new AtomicLong(1);

            @Override
            public Account findOrCreate(Transaction tx, String username) {
                return byUsername.computeIfAbsent(
                        username, u -> new Account(nextId.getAndIncrement(), u, Instant.now()));
            }
        };
        SessionPort sessionPort = new SessionPort() {
            private final Map<String, Long> tokens = new HashMap<>();

            @Override
            public String issue(long accountId) {
                String token = "fake-token-" + accountId;
                tokens.put(token, accountId);
                return token;
            }

            @Override
            public Optional<Long> validateAndRenew(String token) {
                return Optional.ofNullable(tokens.get(token));
            }

            @Override
            public void revoke(String token) {
                tokens.remove(token);
            }
        };
        LoginOrCreateAccountUseCase useCase =
                new LoginOrCreateAccountUseCase(accountPort, sessionPort, action -> action.accept(null));
        return new AccountFacade(useCase);
    }

    private CharacterFacade fakeCharacterFacade() {
        characterPort = new CharacterPort() {
            private final List<Character> characters = new ArrayList<>();
            private final AtomicLong nextId = new AtomicLong(1);
            private final Map<Long, PositionSnapshot> positions = new HashMap<>();

            @Override
            public PositionSnapshot loadPosition(long characterId) {
                return positions.getOrDefault(characterId, new PositionSnapshot(0f, 0f));
            }

            @Override
            public void savePosition(long characterId, float x, float y) {
                positions.put(characterId, new PositionSnapshot(x, y));
            }

            // Số liệu khởi điểm PLACEHOLDER (khớp PostgresCharacterAdapter.STARTING_STAT) — test
            // socket thật ở file này verify pipeline combat, không verify số liệu chỉ số cụ thể.
            @Override
            public CharacterStats loadStats(long characterId) {
                return new CharacterStats(10, 10, 10, 10);
            }

            @Override
            public List<Character> listByAccount(long accountId) {
                return characters.stream().filter(c -> c.accountId() == accountId).toList();
            }

            @Override
            public int countByAccount(long accountId) {
                return listByAccount(accountId).size();
            }

            @Override
            public boolean existsByName(String name) {
                return characters.stream().anyMatch(c -> c.name().equals(name));
            }

            @Override
            public Optional<Character> findByName(String name) {
                return characters.stream().filter(c -> c.name().equals(name)).findFirst();
            }

            @Override
            public Optional<Character> findById(long characterId) {
                return characters.stream().filter(c -> c.id() == characterId).findFirst();
            }

            @Override
            public Character create(dev.kage.character.domain.Transaction tx, NewCharacter newCharacter) {
                Character character = new Character(
                        nextId.getAndIncrement(),
                        newCharacter.accountId(),
                        1,
                        newCharacter.name(),
                        newCharacter.faction(),
                        1,
                        0,
                        0,
                        Instant.now());
                characters.add(character);
                return character;
            }
        };
        CreateCharacterUseCase createUseCase = new CreateCharacterUseCase(characterPort, action -> action.accept(null));
        ListCharactersUseCase listUseCase = new ListCharactersUseCase(characterPort);
        SelectCharacterUseCase selectUseCase = new SelectCharacterUseCase(characterPort);
        return new CharacterFacade(createUseCase, listUseCase, selectUseCase, characterPort);
    }

    // worldFacade.start() chạy tick thật ở file này, nhưng không test nào hạ gục quái tới chết
    // (chỉ verify đòn đánh đầu tiên) — Fake rỗng vẫn đủ, không rơi vào nhánh grantLoot thật.
    private InventoryFacade fakeInventoryFacade() {
        InventoryPort inventoryPort = new InventoryPort() {
            @Override
            public Optional<Inventory> findByCharacter(long characterId) {
                return Optional.of(new Inventory(characterId, 20, List.of()));
            }

            @Override
            public Inventory save(dev.kage.inventory.domain.Transaction tx, Inventory inventory) {
                return inventory; // không dùng trong test này
            }
        };
        DropTablePort dropTablePort = dropTableCode -> List.<DropTableEntry>of();
        AddItemUseCase addItemUseCase = new AddItemUseCase(inventoryPort, action -> action.accept(null));
        GrantLootUseCase grantLootUseCase =
                new GrantLootUseCase(dropTablePort, inventoryPort, action -> action.accept(null));
        // Equip/unequip không dùng trong test này — fake tối thiểu để compile.
        ItemTemplatePort itemTemplatePort = new ItemTemplatePort() {
            @Override
            public Optional<ItemTemplate> findById(long itemTemplateId) {
                return Optional.empty();
            }

            @Override
            public Optional<Long> findIdByCode(String code) {
                return Optional.empty();
            }
        };
        EquipmentPort equipmentPort = new EquipmentPort() {
            @Override
            public Optional<Equipment> findByCharacter(long characterId) {
                return Optional.of(new Equipment(characterId, Map.of()));
            }

            @Override
            public void save(dev.kage.inventory.domain.Transaction tx, Equipment equipment) {
            }
        };
        EquipItemUseCase equipItemUseCase = new EquipItemUseCase(
                inventoryPort, itemTemplatePort, equipmentPort, action -> action.accept(null));
        UnequipItemUseCase unequipItemUseCase =
                new UnequipItemUseCase(equipmentPort, action -> action.accept(null));
        ComputeEquipmentBonusUseCase computeEquipmentBonusUseCase =
                new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort);
        EquipmentUseCases equipmentUseCases =
                new EquipmentUseCases(equipItemUseCase, unequipItemUseCase, computeEquipmentBonusUseCase);
        // Cường hoá không dùng trong test này — fake tối thiểu để compile.
        GoldPort goldPort = (tx, characterId, amount) -> true;
        AuditPort auditPort = (tx, characterId, action, detailJson) -> { };
        EnhancePorts enhancePorts = new EnhancePorts(inventoryPort, goldPort, auditPort, itemTemplatePort);
        EnhanceItemUseCase enhanceItemUseCase =
                new EnhanceItemUseCase(enhancePorts, action -> action.accept(null));
        return new InventoryFacade(addItemUseCase, grantLootUseCase, equipmentUseCases, enhanceItemUseCase);
    }

    // Shop dùng trong 1 test socket thật (list rỗng) — catalog Fake trả rỗng, phần còn lại tối
    // thiểu để compile, cùng convention fakeInventoryFacade().
    private dev.kage.shop.api.ShopFacade fakeShopFacade() {
        dev.kage.shop.domain.ShopCatalogPort catalogPort = new dev.kage.shop.domain.ShopCatalogPort() {
            @Override
            public Optional<dev.kage.shop.domain.ShopListing> findListing(long itemTemplateId) {
                return Optional.empty();
            }

            @Override
            public List<dev.kage.shop.domain.ShopListing> findAll() {
                return List.of();
            }
        };
        dev.kage.shop.domain.GoldPort shopGoldPort = new dev.kage.shop.domain.GoldPort() {
            @Override
            public boolean deductGold(dev.kage.shop.domain.Transaction tx, long characterId, long amount) {
                return true;
            }

            @Override
            public void creditGold(dev.kage.shop.domain.Transaction tx, long characterId, long amount) {
            }
        };
        dev.kage.shop.domain.InventoryGatewayPort inventoryGatewayPort =
                new dev.kage.shop.domain.InventoryGatewayPort() {
            @Override
            public Optional<dev.kage.shop.domain.OwnedItem> findItem(long characterId, long itemId) {
                return Optional.empty();
            }

            @Override
            public boolean grantItem(
                    dev.kage.shop.domain.Transaction tx, long characterId, long itemTemplateId, int quantity) {
                return true;
            }

            @Override
            public boolean removeQuantity(
                    dev.kage.shop.domain.Transaction tx, long characterId, long itemId, int quantity) {
                return true;
            }
        };
        dev.kage.shop.domain.AuditPort shopAuditPort = (tx, characterId, action, detailJson) -> { };
        dev.kage.shop.domain.IdempotencyPort shopIdempotencyPort = (tx, idempotencyKey) -> { };
        dev.kage.shop.application.ShopPorts ports = new dev.kage.shop.application.ShopPorts(
                catalogPort, shopGoldPort, inventoryGatewayPort, shopAuditPort, shopIdempotencyPort);
        dev.kage.shop.application.ListShopItemsUseCase listShopItemsUseCase =
                new dev.kage.shop.application.ListShopItemsUseCase(catalogPort);
        dev.kage.shop.application.BuyItemUseCase buyItemUseCase =
                new dev.kage.shop.application.BuyItemUseCase(ports, action -> action.accept(null));
        dev.kage.shop.application.SellItemUseCase sellItemUseCase =
                new dev.kage.shop.application.SellItemUseCase(ports, action -> action.accept(null));
        return new dev.kage.shop.api.ShopFacade(listShopItemsUseCase, buyItemUseCase, sellItemUseCase);
    }

    // Dialogue dùng trong 1 test socket thật (NPC không tồn tại) — Fake port luôn trả rỗng, phần
    // còn lại tối thiểu để compile, cùng convention fakeShopFacade().
    private dev.kage.dialogue.api.DialogueFacade fakeDialogueFacade() {
        dev.kage.dialogue.domain.NpcTemplatePort npcTemplatePort = npcCode -> Optional.empty();
        dev.kage.dialogue.application.GetDialogueNodeUseCase useCase =
                new dev.kage.dialogue.application.GetDialogueNodeUseCase(npcTemplatePort);
        return new dev.kage.dialogue.api.DialogueFacade(useCase);
    }

    // Quest không dùng trong test này — fake tối thiểu để compile (cùng convention fakeDialogueFacade).
    private dev.kage.quest.api.QuestFacade fakeQuestFacade() {
        dev.kage.quest.domain.QuestProgressPort progressPort = new dev.kage.quest.domain.QuestProgressPort() {
            @Override
            public Optional<dev.kage.quest.domain.QuestProgress> find(long characterId, long questTemplateId) {
                return Optional.empty();
            }

            @Override
            public void save(dev.kage.quest.domain.Transaction tx, dev.kage.quest.domain.QuestProgress progress) {
            }
        };
        dev.kage.quest.domain.QuestTemplatePort templatePort = questCode -> Optional.empty();
        dev.kage.quest.domain.ExpPort expPort = (tx, characterId, amount) -> { };
        dev.kage.quest.domain.QuestInventoryGrantPort inventoryGrantPort =
                (tx, characterId, itemTemplateCode, quantity) -> true;
        dev.kage.quest.domain.AuditPort auditPort = (tx, characterId, action, detailJson) -> { };
        dev.kage.quest.domain.TransactionRunner tx = action -> action.accept(null);

        dev.kage.quest.application.AcceptQuestUseCase acceptQuestUseCase =
                new dev.kage.quest.application.AcceptQuestUseCase(progressPort, templatePort, tx);
        dev.kage.quest.application.ProcessMonsterKilledUseCase processMonsterKilledUseCase =
                new dev.kage.quest.application.ProcessMonsterKilledUseCase(progressPort, templatePort, tx);
        dev.kage.quest.application.QuestRewardPorts rewardPorts =
                new dev.kage.quest.application.QuestRewardPorts(expPort, inventoryGrantPort, auditPort);
        dev.kage.quest.application.ClaimQuestRewardUseCase claimQuestRewardUseCase =
                new dev.kage.quest.application.ClaimQuestRewardUseCase(
                        progressPort, templatePort, rewardPorts, tx);
        return new dev.kage.quest.api.QuestFacade(
                acceptQuestUseCase, processMonsterKilledUseCase, claimQuestRewardUseCase);
    }

    // Friend không dùng trong test này — fake tối thiểu để compile (cùng convention fakeQuestFacade).
    private dev.kage.friend.api.FriendFacade fakeFriendFacade() {
        dev.kage.friend.domain.FriendPort friendPort = new dev.kage.friend.domain.FriendPort() {
            @Override
            public Optional<dev.kage.friend.domain.Friendship> findBetween(long characterIdA, long characterIdB) {
                return Optional.empty();
            }

            @Override
            public dev.kage.friend.domain.Friendship sendRequest(long requesterId, long addresseeId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean accept(long friendshipId, long addresseeId) {
                return false;
            }

            @Override
            public boolean reject(long friendshipId, long addresseeId) {
                return false;
            }

            @Override
            public boolean remove(long characterId, long friendCharacterId) {
                return false;
            }

            @Override
            public List<dev.kage.friend.domain.Friendship> listAccepted(long characterId) {
                return List.of();
            }

            @Override
            public List<dev.kage.friend.domain.Friendship> listPendingIncoming(long characterId) {
                return List.of();
            }
        };
        dev.kage.friend.application.FriendUseCases useCases = new dev.kage.friend.application.FriendUseCases(
                new dev.kage.friend.application.SendFriendRequestUseCase(friendPort),
                new dev.kage.friend.application.AcceptFriendRequestUseCase(friendPort),
                new dev.kage.friend.application.RejectFriendRequestUseCase(friendPort),
                new dev.kage.friend.application.RemoveFriendUseCase(friendPort),
                new dev.kage.friend.application.ListFriendsUseCase(friendPort),
                new dev.kage.friend.application.ListPendingFriendRequestsUseCase(friendPort));
        return new dev.kage.friend.api.FriendFacade(useCases);
    }

    // Mail không dùng trong test này — fake tối thiểu để compile (cùng convention fakeFriendFacade).
    private dev.kage.mail.api.MailFacade fakeMailFacade() {
        dev.kage.mail.domain.MailRepositoryPort mailRepositoryPort = new dev.kage.mail.domain.MailRepositoryPort() {
            @Override
            public dev.kage.mail.domain.Mail send(
                    dev.kage.mail.domain.Transaction tx, dev.kage.mail.domain.NewMail newMail) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public Optional<dev.kage.mail.domain.Mail> findById(long mailId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public Optional<dev.kage.mail.domain.Mail> findByIdForUpdate(
                    dev.kage.mail.domain.Transaction tx, long mailId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public List<dev.kage.mail.domain.Mail> listForRecipient(long recipientId) {
                return List.of();
            }

            @Override
            public boolean markClaimed(dev.kage.mail.domain.Transaction tx, long mailId, long recipientId) {
                return false;
            }

            @Override
            public boolean delete(long mailId, long recipientId) {
                return false;
            }
        };
        dev.kage.mail.domain.GoldPort mailGoldPort = (tx, characterId, amount) -> { };
        dev.kage.mail.domain.InventoryGatewayPort mailInventoryGatewayPort =
                (tx, characterId, itemTemplateId, quantity) -> true;
        dev.kage.mail.domain.AuditPort mailAuditPort = (tx, characterId, action, detailJson) -> { };
        dev.kage.mail.domain.IdempotencyPort mailIdempotencyPort = (tx, idempotencyKey) -> { };
        dev.kage.mail.domain.TransactionRunner mailTx = action -> action.accept(null);
        dev.kage.mail.application.SendMailUseCase sendMailUseCase =
                new dev.kage.mail.application.SendMailUseCase(mailRepositoryPort, mailTx);
        dev.kage.mail.application.ListMailUseCase listMailUseCase =
                new dev.kage.mail.application.ListMailUseCase(mailRepositoryPort);
        dev.kage.mail.application.MailPorts mailPorts = new dev.kage.mail.application.MailPorts(
                mailRepositoryPort, mailGoldPort, mailInventoryGatewayPort, mailAuditPort, mailIdempotencyPort);
        dev.kage.mail.application.ClaimMailUseCase claimMailUseCase =
                new dev.kage.mail.application.ClaimMailUseCase(mailPorts, mailTx);
        dev.kage.mail.application.DeleteMailUseCase deleteMailUseCase =
                new dev.kage.mail.application.DeleteMailUseCase(mailRepositoryPort);
        return new dev.kage.mail.api.MailFacade(sendMailUseCase, listMailUseCase, claimMailUseCase, deleteMailUseCase);
    }

    // Chat không dùng trong test này — fake tối thiểu để compile (cùng convention fakeMailFacade).
    private dev.kage.chat.api.ChatFacade fakeChatFacade() {
        dev.kage.chat.domain.ChatAuditPort chatAuditPort = (characterId, action, detailJson) -> { };
        return new dev.kage.chat.api.ChatFacade(new dev.kage.chat.application.SendChatMessageUseCase(chatAuditPort));
    }

    // Guild không dùng trong test này — fake tối thiểu để compile (cùng convention fakeChatFacade).
    private dev.kage.guild.api.GuildFacade fakeGuildFacade() {
        dev.kage.guild.domain.GuildPort guildPort = new dev.kage.guild.domain.GuildPort() {
            @Override
            public Optional<dev.kage.guild.domain.Guild> findByName(String name) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public Optional<dev.kage.guild.domain.Guild> findById(long guildId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public Optional<dev.kage.guild.domain.GuildMember> findMembershipOf(long characterId) {
                return Optional.empty();
            }

            @Override
            public List<dev.kage.guild.domain.GuildMember> listMembers(long guildId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public dev.kage.guild.domain.Guild createGuild(String name, long leaderCharacterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean disbandGuild(long leaderCharacterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean leaveGuild(long characterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public Optional<dev.kage.guild.domain.GuildJoinRequest> findPendingRequestOfCharacter(long characterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public List<dev.kage.guild.domain.GuildJoinRequest> listPendingRequests(long guildId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public dev.kage.guild.domain.GuildJoinRequest sendJoinRequest(long guildId, long characterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public ApproveResult approveJoinRequest(long requestId, long leaderCharacterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean rejectJoinRequest(long requestId, long leaderCharacterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }
        };
        dev.kage.guild.application.GuildUseCases useCases = new dev.kage.guild.application.GuildUseCases(
                new dev.kage.guild.application.CreateGuildUseCase(guildPort),
                new dev.kage.guild.application.SendGuildJoinRequestUseCase(guildPort),
                new dev.kage.guild.application.ApproveGuildJoinRequestUseCase(guildPort),
                new dev.kage.guild.application.RejectGuildJoinRequestUseCase(guildPort),
                new dev.kage.guild.application.LeaveGuildUseCase(guildPort),
                new dev.kage.guild.application.DisbandGuildUseCase(guildPort),
                new dev.kage.guild.application.KickGuildMemberUseCase(guildPort),
                new dev.kage.guild.application.ListGuildMembersUseCase(guildPort),
                new dev.kage.guild.application.ListPendingGuildJoinRequestsUseCase(guildPort),
                new dev.kage.guild.application.FindMyGuildUseCase(guildPort));
        return new dev.kage.guild.api.GuildFacade(useCases);
    }

    // Party không dùng trong test này — fake tối thiểu để compile (cùng convention fakeGuildFacade).
    /**
     * KHÔNG throw {@code UnsupportedOperationException} (khác các fake facade khác trong file này)
     * — implementation in-memory THẬT, đủ cho kịch bản dungeon (Phase 8 lát cắt 1): tạo party,
     * mời, chấp nhận, tra thành viên. Không cần khoá dòng {@code FOR UPDATE} như
     * {@code PostgresPartyAdapter} — test này single-thread, không có race thật cần chống.
     */
    private dev.kage.party.api.PartyFacade fakePartyFacade() {
        dev.kage.party.domain.PartyPort partyPort = new dev.kage.party.domain.PartyPort() {
            private final Map<Long, dev.kage.party.domain.Party> partiesById = new HashMap<>();
            private final Map<Long, List<dev.kage.party.domain.PartyMember>> membersByPartyId = new HashMap<>();
            private final Map<Long, dev.kage.party.domain.PartyInvite> invitesById = new HashMap<>();
            private final AtomicLong nextPartyId = new AtomicLong(1);
            private final AtomicLong nextInviteId = new AtomicLong(1);

            @Override
            public Optional<dev.kage.party.domain.PartyMember> findMembershipOf(long characterId) {
                return membersByPartyId.values().stream()
                        .flatMap(List::stream)
                        .filter(m -> m.characterId() == characterId)
                        .findFirst();
            }

            @Override
            public List<dev.kage.party.domain.PartyMember> listMembers(long partyId) {
                return List.copyOf(membersByPartyId.getOrDefault(partyId, List.of()));
            }

            @Override
            public dev.kage.party.domain.Party createParty(long leaderCharacterId) {
                long partyId = nextPartyId.getAndIncrement();
                dev.kage.party.domain.Party party =
                        new dev.kage.party.domain.Party(partyId, leaderCharacterId, Instant.now());
                partiesById.put(partyId, party);
                dev.kage.party.domain.PartyMember leader = new dev.kage.party.domain.PartyMember(
                        partyId, leaderCharacterId, dev.kage.party.domain.PartyRole.LEADER, Instant.now());
                membersByPartyId.put(partyId, new ArrayList<>(List.of(leader)));
                return party;
            }

            @Override
            public Optional<dev.kage.party.domain.PartyInvite> findPendingInviteOfInvitee(long characterId) {
                return invitesById.values().stream()
                        .filter(i -> i.inviteeCharacterId() == characterId)
                        .findFirst();
            }

            @Override
            public List<dev.kage.party.domain.PartyInvite> listPendingInvitesOfInvitee(long characterId) {
                return invitesById.values().stream()
                        .filter(i -> i.inviteeCharacterId() == characterId)
                        .toList();
            }

            @Override
            public dev.kage.party.domain.PartyInvite invite(long partyId, long inviteeCharacterId) {
                long inviteId = nextInviteId.getAndIncrement();
                dev.kage.party.domain.PartyInvite invite = new dev.kage.party.domain.PartyInvite(
                        inviteId, partyId, inviteeCharacterId, Instant.now());
                invitesById.put(inviteId, invite);
                return invite;
            }

            @Override
            public AcceptResult acceptInvite(long inviteId, long characterId) {
                dev.kage.party.domain.PartyInvite invite = invitesById.get(inviteId);
                if (invite == null || invite.inviteeCharacterId() != characterId) {
                    return AcceptResult.NOT_FOUND;
                }
                invitesById.remove(inviteId);
                dev.kage.party.domain.PartyMember member = new dev.kage.party.domain.PartyMember(
                        invite.partyId(), characterId, dev.kage.party.domain.PartyRole.MEMBER, Instant.now());
                membersByPartyId.computeIfAbsent(invite.partyId(), id -> new ArrayList<>()).add(member);
                return AcceptResult.OK;
            }

            @Override
            public boolean rejectInvite(long inviteId, long characterId) {
                dev.kage.party.domain.PartyInvite invite = invitesById.get(inviteId);
                if (invite == null || invite.inviteeCharacterId() != characterId) {
                    return false;
                }
                invitesById.remove(inviteId);
                return true;
            }

            @Override
            public LeaveResult leaveParty(long characterId) {
                Optional<dev.kage.party.domain.PartyMember> membership = findMembershipOf(characterId);
                if (membership.isEmpty()) {
                    return LeaveResult.NOT_IN_PARTY;
                }
                membersByPartyId.get(membership.get().partyId()).removeIf(m -> m.characterId() == characterId);
                return LeaveResult.OK;
            }

            @Override
            public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }
        };
        dev.kage.party.application.PartyUseCases useCases = new dev.kage.party.application.PartyUseCases(
                new dev.kage.party.application.CreatePartyUseCase(partyPort),
                new dev.kage.party.application.InvitePartyMemberUseCase(partyPort),
                new dev.kage.party.application.AcceptPartyInviteUseCase(partyPort),
                new dev.kage.party.application.RejectPartyInviteUseCase(partyPort),
                new dev.kage.party.application.LeavePartyUseCase(partyPort),
                new dev.kage.party.application.KickPartyMemberUseCase(partyPort),
                new dev.kage.party.application.ListPartyMembersUseCase(partyPort),
                new dev.kage.party.application.ListPendingPartyInvitesUseCase(partyPort),
                new dev.kage.party.application.FindMyPartyUseCase(partyPort));
        return new dev.kage.party.api.PartyFacade(useCases);
    }

    // Trade không dùng trong test này — fake tối thiểu để compile (cùng convention fakePartyFacade).
    private dev.kage.trade.api.TradeFacade fakeTradeFacade() {
        dev.kage.trade.domain.TradeSessionPort sessionPort = new dev.kage.trade.domain.TradeSessionPort() {
            @Override
            public Optional<dev.kage.trade.domain.TradeSession> findActiveSessionOfCharacter(long characterId) {
                return Optional.empty();
            }

            @Override
            public dev.kage.trade.domain.TradeSession createRequest(long requesterCharacterId, long targetCharacterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean acceptRequest(long sessionId, long characterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean deleteSessionIfParticipant(long sessionId, long characterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean replaceOffer(long sessionId, long characterId, long goldAmount,
                    List<dev.kage.trade.domain.OfferedItemInput> items) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public Optional<dev.kage.trade.domain.TradeSessionSnapshot> findSnapshot(long sessionId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public Optional<dev.kage.trade.domain.TradeSessionSnapshot> lockForExecution(
                    dev.kage.trade.domain.Transaction tx, long sessionId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public void setConfirmed(dev.kage.trade.domain.Transaction tx, long sessionId, long characterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public void deleteSession(dev.kage.trade.domain.Transaction tx, long sessionId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }
        };
        dev.kage.trade.domain.TradeInventoryPort inventoryPort = new dev.kage.trade.domain.TradeInventoryPort() {
            @Override
            public Optional<dev.kage.trade.domain.OwnedItemSnapshot> findItemById(long itemId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public TransferOutcome transferItem(dev.kage.trade.domain.Transaction tx, TransferRequest request) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }
        };
        dev.kage.trade.domain.TradeGoldPort goldPort = new dev.kage.trade.domain.TradeGoldPort() {
            @Override
            public boolean deductGold(dev.kage.trade.domain.Transaction tx, long characterId, long amount) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public void creditGold(dev.kage.trade.domain.Transaction tx, long characterId, long amount) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }
        };
        dev.kage.trade.domain.TradeAuditPort auditPort = (tx, characterId, action, detailJson) -> { };
        dev.kage.trade.domain.IdempotencyPort idempotencyPort = (tx, idempotencyKey) -> { };
        dev.kage.trade.application.TradePorts ports = new dev.kage.trade.application.TradePorts(
                sessionPort, inventoryPort, goldPort, auditPort, idempotencyPort);
        dev.kage.trade.application.TradeUseCases useCases = new dev.kage.trade.application.TradeUseCases(
                new dev.kage.trade.application.RequestTradeUseCase(sessionPort),
                new dev.kage.trade.application.AcceptTradeRequestUseCase(sessionPort),
                new dev.kage.trade.application.CancelTradeUseCase(sessionPort),
                new dev.kage.trade.application.SetTradeOfferUseCase(sessionPort),
                new dev.kage.trade.application.ConfirmTradeUseCase(ports, action -> action.accept(null)),
                new dev.kage.trade.application.GetTradeStatusUseCase(sessionPort, inventoryPort));
        return new dev.kage.trade.api.TradeFacade(useCases);
    }

    @AfterEach
    void stopServer() {
        server.stop();
        worldFacade.stop();
        characterRegistry.stop();
    }

    @Test
    @Timeout(15)
    void shouldSavePosition_whenServerShutsDownWhileClientConnected() throws Exception {
        // PHÁT HIỆN THẬT (xem javadoc GatewayServer#stop): bản trước tắt authExecutor TRƯỚC khi
        // đóng channel — channelInactive lúc shutdown bị RejectedExecutionException âm thầm, mất
        // vị trí cuối. Test này verify trực tiếp qua Fake Port, không chỉ đọc code.
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            long characterId = connectCreateAndSelect(socket, in, "shutdowntest");

            MoveCommand moveCommand = MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());
            readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE); // xác nhận world đã bắt đầu di chuyển

            Thread.sleep(200); // để vài tick 60Hz chạy — x phải > 0 lúc này nếu đang di chuyển thật

            // MoveCommand thứ 2 (dừng lại) — MoveResponse trả về là vị trí HIỆN TẠI, dùng làm giá
            // trị kỳ vọng để so sánh với DB sau khi tắt server (không đoán khoảng cách đã đi).
            MoveCommand stopCommand = MoveCommand.newBuilder().setDirX(0f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 11, stopCommand.toByteArray());
            MoveResponse stopResponse =
                    MoveResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE).payload());
            assertThat(stopResponse.getX()).isGreaterThan(0f); // sanity: di chuyển thật đã xảy ra

            server.stop(); // KHÔNG đợi @AfterEach — test tự gọi để verify NGAY sau khi trả về

            PositionSnapshot savedPosition = characterPort.loadPosition(characterId);
            assertThat(savedPosition.x()).isEqualTo(stopResponse.getX());
        }
    }

    @Test
    @Timeout(10)
    void clientShouldReceivePong_overRealSocket() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            PingCommand ping = PingCommand.newBuilder().setClientTimestampMs(999L).build();
            PacketHeader header = new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, 1);
            byte[] frame = PacketCodec.encodeFrame(header, ping.toByteArray());
            out.writeInt(frame.length);
            out.write(frame);
            out.flush();

            PacketCodec.Frame decoded = readFrame(in);

            assertThat(decoded.header().opcode()).isEqualTo(Opcode.OPCODE_PONG_VALUE);
            assertThat(decoded.header().sequenceId()).isEqualTo(1);

            PongResponse pong = PongResponse.parseFrom(decoded.payload());
            assertThat(pong.getClientTimestampMs()).isEqualTo(999L);
        }
    }

    @Test
    @Timeout(10)
    void serverShouldAcceptMultipleConnectionsConcurrently() throws Exception {
        try (Socket socketA = new Socket("127.0.0.1", server.port());
                Socket socketB = new Socket("127.0.0.1", server.port())) {
            sendPing(socketA, 1, 111L);
            sendPing(socketB, 1, 222L);

            PongResponse pongA = PongResponse.parseFrom(
                    readFrame(new DataInputStream(socketA.getInputStream())).payload());
            PongResponse pongB = PongResponse.parseFrom(
                    readFrame(new DataInputStream(socketB.getInputStream())).payload());

            // Mỗi kết nối nhận ĐÚNG pong của mình — không lẫn giữa các connection (SERVER.md §1).
            assertThat(pongA.getClientTimestampMs()).isEqualTo(111L);
            assertThat(pongB.getClientTimestampMs()).isEqualTo(222L);
        }
    }

    @Test
    @Timeout(15)
    void twoClientsShouldSeeEachOtherMove_overRealSockets() throws Exception {
        // Verify đúng Phase 3 Exit Criteria (ROADMAP.md): "2 client kết nối cùng server, thấy
        // nhau di chuyển" — qua chính giao thức dây (socket thật), không chỉ nội bộ WorldFacade.
        try (Socket socketA = new Socket("127.0.0.1", server.port());
                Socket socketB = new Socket("127.0.0.1", server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            DataInputStream inB = new DataInputStream(socketB.getInputStream());

            // Account.username giới hạn 3-20 ký tự — không dùng UUID đầy đủ được.
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            long characterA = connectCreateAndSelect(socketA, inA, "a" + suffix);
            long characterB = connectCreateAndSelect(socketB, inB, "b" + suffix);

            // Cả 2 spawn cùng điểm (0,0) — vào AoI của nhau ngay tick kế tiếp sau khi attach.
            CharacterSpawnedEvent spawnSeenByA = CharacterSpawnedEvent.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE).payload());
            assertThat(spawnSeenByA.getCharacterId()).isEqualTo(characterB);
            CharacterSpawnedEvent spawnSeenByB = CharacterSpawnedEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE).payload());
            assertThat(spawnSeenByB.getCharacterId()).isEqualTo(characterA);

            // A di chuyển — chỉ gửi HƯỚNG (movement.proto), server tự nhân tốc độ.
            MoveCommand moveCommand = MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socketA, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());

            // A tự nhận MoveResponse (reconciliation, CLIENT.md §6) khớp sequenceId đã gửi.
            PacketCodec.Frame moveResponseFrame = readUntilOpcode(inA, Opcode.OPCODE_MOVE_RESPONSE_VALUE);
            assertThat(moveResponseFrame.header().sequenceId()).isEqualTo(10);
            MoveResponse moveResponse = MoveResponse.parseFrom(moveResponseFrame.payload());
            assertThat(moveResponse.getX()).isGreaterThanOrEqualTo(0f);

            // B nhận CharacterMovedEvent về A (AoI — SERVER.md §6), vị trí đã đổi khỏi (0,0).
            CharacterMovedEvent movedSeenByB = CharacterMovedEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE).payload());
            assertThat(movedSeenByB.getCharacterId()).isEqualTo(characterA);
            assertThat(movedSeenByB.getX()).isGreaterThan(0f);
        }
    }

    @Test
    // 20s (khác 15s mặc định file này) — kịch bản này có nhiều round-trip hơn hẳn (3 socket, party
    // setup, 2 lần enter, 2 despawn, move, exit×2), cần thêm dư địa dưới tải máy chạy test nặng
    // (VD ngay sau loạt test Testcontainers khác trong `./gradlew build`).
    @Timeout(20)
    void partyShouldEnterAndExitDungeonAsSeparateWorld_overRealSockets() throws Exception {
        // Phase 8 lát cắt 1 (ADR-026): "cả party cùng vào 1 World riêng, tick độc lập world
        // chính" — verify qua chính giao thức dây, không chỉ nội bộ DungeonInstanceRegistry.
        try (Socket socketA = new Socket("127.0.0.1", server.port());
                Socket socketB = new Socket("127.0.0.1", server.port());
                Socket socketC = new Socket("127.0.0.1", server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            socketC.setSoTimeout(5000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            DataInputStream inB = new DataInputStream(socketB.getInputStream());
            DataInputStream inC = new DataInputStream(socketC.getInputStream());

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            long characterA = connectCreateAndSelect(socketA, inA, "da" + suffix);
            long characterB = connectCreateAndSelect(socketB, inB, "db" + suffix);
            long characterC = connectCreateAndSelect(socketC, inC, "dc" + suffix);

            // Party A+B qua PartyFacade trực tiếp (đã có test riêng cho luồng mời/chấp nhận qua
            // socket ở feature-party — test này chỉ cần TIỀN ĐIỀU KIỆN "A+B cùng party" để tập
            // trung verify cơ chế dungeon). C KHÔNG vào party — ở lại world chính làm chứng nhân.
            partyFacade.createParty(characterA);
            partyFacade.invite(characterA, characterB);
            long inviteId = partyFacade.listPendingInvites(characterB).get(0).inviteId();
            partyFacade.acceptInvite(inviteId, characterB);

            // A gửi EnterDungeonCommand — kéo THEO CẢ B (cùng party, đang online) vào CHUNG 1 instance.
            EnterDungeonCommand enterCommand =
                    EnterDungeonCommand.newBuilder().setDungeonCode("sample_dungeon").build();
            sendFrame(socketA, Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, 20, enterCommand.toByteArray());
            EnterDungeonResponse enterResponseA = EnterDungeonResponse.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE).payload());
            assertThat(enterResponseA.getOutcome()).isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);

            // B gửi lại CŨNG EnterDungeonCommand — đã bị A kéo vào sẵn, outcome phải là ALREADY_IN_DUNGEON.
            sendFrame(socketB, Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, 20, enterCommand.toByteArray());
            EnterDungeonResponse enterResponseB = EnterDungeonResponse.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE).payload());
            assertThat(enterResponseB.getOutcome())
                    .isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_ALREADY_IN_DUNGEON);

            // C (world chính) phải thấy CẢ A lẫn B despawn — chứng minh 2 người đã RỜI world chính
            // thật (không chỉ "coi như" rời), vì AoI world chính không còn giữ entity của họ nữa.
            java.util.HashSet<Long> despawnedIds = new java.util.HashSet<Long>();
            despawnedIds.add(CharacterDespawnedEvent.parseFrom(
                    readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE).payload())
                    .getCharacterId());
            despawnedIds.add(CharacterDespawnedEvent.parseFrom(
                    readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE).payload())
                    .getCharacterId());
            assertThat(despawnedIds).containsExactlyInAnyOrder(characterA, characterB);

            // A di chuyển TRONG dungeon — B (cùng instance) phải thấy, chứng minh 2 người đang
            // tick chung 1 World riêng (không phải world chính, đã verify despawn ở trên).
            MoveCommand moveCommand = MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socketA, Opcode.OPCODE_MOVE_COMMAND_VALUE, 21, moveCommand.toByteArray());
            PacketCodec.Frame moveResponseFrame = readUntilOpcode(inA, Opcode.OPCODE_MOVE_RESPONSE_VALUE);
            assertThat(moveResponseFrame.header().sequenceId()).isEqualTo(21);

            CharacterMovedEvent movedSeenByB = CharacterMovedEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE).payload());
            assertThat(movedSeenByB.getCharacterId()).isEqualTo(characterA);
            assertThat(movedSeenByB.getX()).isGreaterThan(0f);

            // A rời dungeon — về ĐÚNG vị trí thật trước khi vào (0,0), C (world chính) thấy A
            // spawn lại — chứng minh A đã quay về world chính thành công.
            ExitDungeonCommand exitCommand = ExitDungeonCommand.newBuilder().build();
            sendFrame(socketA, Opcode.OPCODE_EXIT_DUNGEON_COMMAND_VALUE, 22, exitCommand.toByteArray());
            ExitDungeonResponse exitResponseA = ExitDungeonResponse.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE).payload());
            assertThat(exitResponseA.getOutcome()).isEqualTo(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK);

            CharacterSpawnedEvent spawnSeenByC = CharacterSpawnedEvent.parseFrom(
                    readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE).payload());
            assertThat(spawnSeenByC.getCharacterId()).isEqualTo(characterA);
            assertThat(spawnSeenByC.getX()).isEqualTo(0f);
            assertThat(spawnSeenByC.getY()).isEqualTo(0f);

            // B rời nốt — dungeon instance rỗng, World của nó tự dừng (DungeonInstanceRegistry).
            sendFrame(socketB, Opcode.OPCODE_EXIT_DUNGEON_COMMAND_VALUE, 22, exitCommand.toByteArray());
            ExitDungeonResponse exitResponseB = ExitDungeonResponse.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE).payload());
            assertThat(exitResponseB.getOutcome()).isEqualTo(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK);
        }
    }

    @Test
    // 35s — trận đấu boss thời gian thực ~12-13s (BOSS_MAX_HP=320, cùng lý do WorldFacadeTest)
    // cộng chi phí gateway/socket thật, hơn hẳn 20s của test dungeon lát cắt 1.
    @Timeout(35)
    void partyShouldFightBossInDungeon_overRealSockets() throws Exception {
        // Phase 8 lát cắt 2 — boss tự spawn khi dungeon instance được tạo
        // (DungeonInstanceRegistry.createInstance gọi spawnBoss() sau start()). Verify qua chính
        // giao thức dây: boss xuất hiện, đánh xuyên nhiều pha (heavy_strike/enrage — SkillResultEvent
        // TÁI DÙNG, không opcode riêng), chết → MonsterDespawnedEvent.
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(10000);
            DataInputStream in = new DataInputStream(socket.getInputStream());

            long characterId = connectCreateAndSelect(
                    socket, in, "boss" + UUID.randomUUID().toString().substring(0, 6));
            partyFacade.createParty(characterId); // party 1 người — đủ điều kiện vào dungeon.

            EnterDungeonCommand enterCommand =
                    EnterDungeonCommand.newBuilder().setDungeonCode("sample_dungeon").build();
            sendFrame(socket, Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, 30, enterCommand.toByteArray());
            EnterDungeonResponse enterResponse = EnterDungeonResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE).payload());
            assertThat(enterResponse.getOutcome()).isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);

            MonsterSpawnedEvent bossSpawned = MonsterSpawnedEvent.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE).payload());
            assertThat(bossSpawned.getMonsterCode()).isEqualTo("sample_boss");
            long bossId = bossSpawned.getMonsterId();

            SetTargetCommand setTarget = SetTargetCommand.newBuilder().setTargetEntityId(bossId).build();
            sendFrame(socket, Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, 31, setTarget.toByteArray());

            // Boss phải dùng ÍT NHẤT 1 chiêu đặc biệt trước khi chết — đọc frame liên tục, phân
            // loại theo opcode, dừng khi thấy 1 trong 2 (hoặc boss chết trước, coi như thất bại).
            boolean sawSpecialAction = false;
            boolean bossDied = false;
            for (int i = 0; i < 5000 && !bossDied; i++) {
                PacketCodec.Frame frame = readFrame(in);
                if (frame.header().opcode() == Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE) {
                    dev.kage.protocol.SkillResultEvent skillResult =
                            dev.kage.protocol.SkillResultEvent.parseFrom(frame.payload());
                    if (("heavy_strike".equals(skillResult.getSkillCode())
                            || "enrage".equals(skillResult.getSkillCode()))
                            && skillResult.getAttackerEntityId() == bossId) {
                        sawSpecialAction = true;
                    }
                } else if (frame.header().opcode() == Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE) {
                    dev.kage.protocol.MonsterDespawnedEvent despawned =
                            dev.kage.protocol.MonsterDespawnedEvent.parseFrom(frame.payload());
                    if (despawned.getMonsterId() == bossId) {
                        bossDied = true;
                    }
                }
            }
            assertThat(bossDied).as("boss phải chết trong thời gian chờ").isTrue();
            assertThat(sawSpecialAction).as("boss phải dùng heavy_strike hoặc enrage trước khi chết").isTrue();
        }
    }

    @Test
    @Timeout(15)
    void clientShouldDamageMonster_overRealSockets() throws Exception {
        // Verify Phase 4 lát cắt 1 qua chính giao thức dây: di chuyển tới gần quái bù nhìn (spawn
        // cố định (5,5), WorldFacade) → SetTargetCommand → nhận AttackResultEvent thật.
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            long characterId = connectCreateAndSelect(socket, in, "atk" + UUID.randomUUID().toString().substring(0, 6));

            // Nhân vật mới spawn (0,0) — quái ở (5,5), cách ~7.07m, đã trong AoI (15m) nên
            // MonsterSpawnedEvent tới ngay, nhưng CHƯA trong tầm đánh (2m) — phải di chuyển lại gần.
            MonsterSpawnedEvent monsterSpawned = MonsterSpawnedEvent.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE).payload());
            long monsterId = monsterSpawned.getMonsterId();

            // Hướng chéo (0.7071, 0.7071) — chuẩn hoá, giống hệt client thật gửi (server tự nhân
            // tốc độ, movement.proto). ~1.2s ở 5 m/s đưa nhân vật vào trong tầm đánh (còn cách quái ~1m).
            MoveCommand moveCommand = MoveCommand.newBuilder().setDirX(0.7071f).setDirY(0.7071f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());
            Thread.sleep(1200);
            MoveCommand stopCommand = MoveCommand.newBuilder().setDirX(0f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 11, stopCommand.toByteArray());

            SetTargetCommand setTarget = SetTargetCommand.newBuilder().setTargetEntityId(monsterId).build();
            sendFrame(socket, Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, 12, setTarget.toByteArray());

            // PHÁT HIỆN THẬT (Phase 8 lát cắt 2): quái giờ đánh trả CŨNG tới được client (trước đây
            // bị rơi mất với nhân vật chơi solo — xem WorldFacade.handleCombatResults) — 2 chiều
            // đòn (nhân vật→quái, quái→nhân vật) có thể tới theo BẤT KỲ thứ tự nào, lọc đúng đòn
            // của NHÂN VẬT thay vì giả định đòn ĐẦU TIÊN luôn là của nó.
            AttackResultEvent attackResult;
            do {
                attackResult = AttackResultEvent.parseFrom(
                        readUntilOpcode(in, Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE).payload());
            } while (attackResult.getAttackerEntityId() != characterId);
            assertThat(attackResult.getTargetEntityId()).isEqualTo(monsterId);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveEquipItemResponse_overRealSockets() throws Exception {
        // Phase 5 lát cắt "mặc đồ": verify qua chính giao thức dây, không chỉ Facade trong bộ nhớ.
        // fakeInventoryFacade() ở file này luôn trả túi đồ RỖNG (Fake, không DB thật) nên item
        // itemId=1 chắc chắn KHÔNG tồn tại — test đúng nhánh ITEM_NOT_FOUND, verify handler mới
        // (opcode dispatch, decode protobuf, chạy trên authExecutor, encode response) hoạt động
        // đúng dây thật, không phải nhánh "mặc thành công" (cái đó PostgresInventoryAdapter/
        // PostgresEquipmentAdapterIntegrationTest + EquipItemUseCaseTest đã phủ ở tầng dưới).
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "equip" + UUID.randomUUID().toString().substring(0, 6));

            EquipItemCommand equipCommand = EquipItemCommand.newBuilder()
                    .setItemId(1L)
                    .setSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON)
                    .build();
            sendFrame(socket, Opcode.OPCODE_EQUIP_ITEM_COMMAND_VALUE, 10, equipCommand.toByteArray());

            EquipItemResponse response = EquipItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_EQUIP_ITEM_RESPONSE_VALUE).payload());
            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getErrorKey()).isEqualTo("error.item_not_found");
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveUnequipItemResponse_overRealSockets() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "unequip" + UUID.randomUUID().toString().substring(0, 6));

            UnequipItemCommand unequipCommand =
                    UnequipItemCommand.newBuilder().setSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON).build();
            sendFrame(socket, Opcode.OPCODE_UNEQUIP_ITEM_COMMAND_VALUE, 10, unequipCommand.toByteArray());

            UnequipItemResponse response = UnequipItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_UNEQUIP_ITEM_RESPONSE_VALUE).payload());
            assertThat(response.getSuccess()).isFalse(); // slot rỗng — không có gì để gỡ
            assertThat(response.getErrorKey()).isEqualTo("error.slot_not_equipped");
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveEnhanceItemResponse_overRealSockets() throws Exception {
        // Cùng lý do với clientShouldReceiveEquipItemResponse_overRealSockets: fakeInventoryFacade()
        // luôn trả túi đồ RỖNG (Fake, không DB thật) nên itemId=1 chắc chắn không tồn tại — test
        // đúng nhánh ITEM_NOT_FOUND, verify dây thật (opcode dispatch → decode → Virtual Thread →
        // encode response), không phải nhánh cường hoá thành công (đã phủ ở EnhanceItemUseCaseTest/
        // EnhanceItemUseCaseIntegrationTest ở tầng dưới).
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "enhance" + UUID.randomUUID().toString().substring(0, 6));

            EnhanceItemCommand enhanceCommand = EnhanceItemCommand.newBuilder().setItemId(1L).build();
            sendFrame(socket, Opcode.OPCODE_ENHANCE_ITEM_COMMAND_VALUE, 10, enhanceCommand.toByteArray());

            EnhanceItemResponse response = EnhanceItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_ENHANCE_ITEM_RESPONSE_VALUE).payload());
            assertThat(response.getOutcome()).isEqualTo(EnhanceOutcome.ENHANCE_OUTCOME_ITEM_NOT_FOUND);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveEmptyShopListing_overRealSockets() throws Exception {
        // fakeShopFacade() luôn trả catalog RỖNG (Fake, không DB thật) — verify dây thật (opcode
        // dispatch → decode → Virtual Thread → encode response), không phải nội dung catalog thật
        // (đã phủ ở ShopIntegrationTest tầng dưới).
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "shoplist" + UUID.randomUUID().toString().substring(0, 6));

            ListShopItemsRequest request = ListShopItemsRequest.newBuilder().build();
            sendFrame(socket, Opcode.OPCODE_LIST_SHOP_ITEMS_REQUEST_VALUE, 10, request.toByteArray());

            ListShopItemsResponse response = ListShopItemsResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE).payload());
            assertThat(response.getListingsList()).isEmpty();
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveBuyItemResponse_overRealSockets() throws Exception {
        // Cùng lý do clientShouldReceiveEnhanceItemResponse_overRealSockets: catalog rỗng nên
        // itemTemplateId=1 chắc chắn không có trong shop — verify đúng nhánh ITEM_NOT_IN_SHOP.
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "buy" + UUID.randomUUID().toString().substring(0, 6));

            BuyItemCommand command = BuyItemCommand.newBuilder().setItemTemplateId(1L).setQuantity(1).build();
            sendFrame(socket, Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, 10, command.toByteArray());

            BuyItemResponse response = BuyItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE).payload());
            assertThat(response.getOutcome()).isEqualTo(BuyOutcome.BUY_OUTCOME_ITEM_NOT_IN_SHOP);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveSellItemResponse_overRealSockets() throws Exception {
        // fakeShopFacade().findItem() luôn trả rỗng — verify đúng nhánh ITEM_NOT_FOUND.
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "sell" + UUID.randomUUID().toString().substring(0, 6));

            SellItemCommand command = SellItemCommand.newBuilder().setItemId(1L).setQuantity(1).build();
            sendFrame(socket, Opcode.OPCODE_SELL_ITEM_COMMAND_VALUE, 10, command.toByteArray());

            SellItemResponse response = SellItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE).payload());
            assertThat(response.getOutcome()).isEqualTo(SellOutcome.SELL_OUTCOME_ITEM_NOT_FOUND);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveErrorResponse_whenTalkingToUnknownNpc_overRealSockets() throws Exception {
        // fakeDialogueFacade()'s NpcTemplatePort luôn trả rỗng — verify đúng lỗi 404 (không có
        // opcode DialogueNodeResponse riêng cho nhánh này, sendError dùng chung OPCODE_ERROR).
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "npc" + UUID.randomUUID().toString().substring(0, 6));

            TalkToNpcCommand command = TalkToNpcCommand.newBuilder().setNpcCode("town_elder").build();
            sendFrame(socket, Opcode.OPCODE_TALK_TO_NPC_COMMAND_VALUE, 10, command.toByteArray());

            ErrorResponse response = ErrorResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_ERROR_VALUE).payload());
            assertThat(response.getErrorKey()).isEqualTo("error.npc_not_found");
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveClaimQuestRewardResponse_overRealSockets() throws Exception {
        // fakeQuestFacade()'s QuestTemplatePort luôn trả rỗng — verify đúng nhánh QUEST_NOT_FOUND
        // (kiểm tra template TRƯỚC progress, xem ClaimQuestRewardUseCase.execute).
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "quest" + UUID.randomUUID().toString().substring(0, 6));

            ClaimQuestRewardCommand command =
                    ClaimQuestRewardCommand.newBuilder().setQuestCode("kill_training_dummies").build();
            sendFrame(socket, Opcode.OPCODE_CLAIM_QUEST_REWARD_COMMAND_VALUE, 10, command.toByteArray());

            ClaimQuestRewardResponse response = ClaimQuestRewardResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_CLAIM_QUEST_REWARD_RESPONSE_VALUE).payload());
            assertThat(response.getOutcome())
                    .isEqualTo(ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_QUEST_NOT_FOUND);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveSendFriendRequestResponse_overRealSockets() throws Exception {
        // FakeCharacterPort chỉ chứa nhân vật vừa tạo ở kết nối NÀY — tên bất kỳ khác chắc chắn
        // không tồn tại, verify đúng nhánh TARGET_NOT_FOUND (dây thật: opcode dispatch → decode →
        // Virtual Thread → CharacterFacade.findByName → encode response).
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "friend" + UUID.randomUUID().toString().substring(0, 6));

            SendFriendRequestCommand command =
                    SendFriendRequestCommand.newBuilder().setTargetName("khong_ton_tai").build();
            sendFrame(socket, Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE, 10, command.toByteArray());

            SendFriendRequestResponse response = SendFriendRequestResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE).payload());
            assertThat(response.getOutcome())
                    .isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_TARGET_NOT_FOUND);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveSendMailResponse_overRealSockets() throws Exception {
        // Cùng lý do clientShouldReceiveSendFriendRequestResponse_overRealSockets: tên người nhận
        // bất kỳ chắc chắn không tồn tại, verify đúng nhánh RECIPIENT_NOT_FOUND — nhánh này KHÔNG
        // chạm tới fakeMailFacade()'s MailRepositoryPort (ném exception nếu bị gọi thật).
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "mail" + UUID.randomUUID().toString().substring(0, 6));

            SendMailCommand command = SendMailCommand.newBuilder()
                    .setRecipientName("khong_ton_tai")
                    .setSubject("s")
                    .setBody("b")
                    .build();
            sendFrame(socket, Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE, 10, command.toByteArray());

            SendMailResponse response =
                    SendMailResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_SEND_MAIL_RESPONSE_VALUE).payload());
            assertThat(response.getOutcome()).isEqualTo(SendMailOutcome.SEND_MAIL_OUTCOME_RECIPIENT_NOT_FOUND);
        }
    }

    @Test
    @Timeout(15)
    void twoClientsShouldSeeWorldChatMessage_overRealSockets() throws Exception {
        // World chat = broadcast toàn server, KHÔNG cần AoI/cùng vị trí — verify dây thật (opcode
        // dispatch → decode → Virtual Thread → ChatFacade.sendMessage → ConnectedCharacterRegistry
        // .broadcastToAll → encode) cho CẢ người gửi (tự thấy tin của mình) lẫn người khác.
        try (Socket socketA = new Socket("127.0.0.1", server.port());
                Socket socketB = new Socket("127.0.0.1", server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            DataInputStream inB = new DataInputStream(socketB.getInputStream());
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            connectCreateAndSelect(socketA, inA, "wa" + suffix);
            connectCreateAndSelect(socketB, inB, "wb" + suffix);

            SendChatMessageCommand command = SendChatMessageCommand.newBuilder()
                    .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                    .setBody("xin chào thế giới")
                    .build();
            sendFrame(socketA, Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, 10, command.toByteArray());

            SendChatMessageResponse ackResponse = SendChatMessageResponse.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE).payload());
            assertThat(ackResponse.getOutcome()).isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK);

            ChatMessageEvent eventSeenByA =
                    ChatMessageEvent.parseFrom(readUntilOpcode(inA, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE).payload());
            assertThat(eventSeenByA.getBody()).isEqualTo("xin chào thế giới");
            ChatMessageEvent eventSeenByB =
                    ChatMessageEvent.parseFrom(readUntilOpcode(inB, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE).payload());
            assertThat(eventSeenByB.getBody()).isEqualTo("xin chào thế giới");
        }
    }

    private static long connectCreateAndSelect(Socket socket, DataInputStream in, String username)
            throws Exception {
        ConnectRequest connectRequest = ConnectRequest.newBuilder().setUsername(username).build();
        sendFrame(socket, Opcode.OPCODE_CONNECT_REQUEST_VALUE, 1, connectRequest.toByteArray());
        ConnectResponse.parseFrom(readFrame(in).payload());

        // Character.validateName: chỉ chữ/số, KHÔNG dấu gạch ngang (feature-character/CLAUDE.md).
        CreateCharacterRequest createRequest = CreateCharacterRequest.newBuilder()
                .setName("Hero" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        sendFrame(socket, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 2, createRequest.toByteArray());
        CreateCharacterResponse createResponse = CreateCharacterResponse.parseFrom(readFrame(in).payload());
        long characterId = createResponse.getCharacter().getId();

        SelectCharacterRequest selectRequest = SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        sendFrame(socket, Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, 3, selectRequest.toByteArray());
        readFrame(in); // SelectCharacterResponse — nội dung đã verify ở GatewayPipelineTest

        return characterId;
    }

    private static void sendFrame(Socket socket, int opcode, int sequenceId, byte[] payload) throws Exception {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        byte[] frame = PacketCodec.encodeFrame(header, payload);
        out.writeInt(frame.length);
        out.write(frame);
        out.flush();
    }

    /** AoI Event tới bất kỳ lúc nào giữa các Response khác — đọc tới khi thấy đúng opcode cần. */
    private static PacketCodec.Frame readUntilOpcode(DataInputStream in, int expectedOpcode) throws Exception {
        for (int attempt = 0; attempt < 1000; attempt++) {
            PacketCodec.Frame frame = readFrame(in);
            if (frame.header().opcode() == expectedOpcode) {
                return frame;
            }
        }
        throw new AssertionError("Không thấy opcode " + expectedOpcode + " sau 1000 frame");
    }

    private static void sendPing(Socket socket, int sequenceId, long clientTimestampMs) throws Exception {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        PingCommand ping = PingCommand.newBuilder().setClientTimestampMs(clientTimestampMs).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, sequenceId);
        byte[] frame = PacketCodec.encodeFrame(header, ping.toByteArray());
        out.writeInt(frame.length);
        out.write(frame);
        out.flush();
    }

    private static PacketCodec.Frame readFrame(DataInputStream in) throws Exception {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return PacketCodec.decodeFrame(bytes);
    }
}
