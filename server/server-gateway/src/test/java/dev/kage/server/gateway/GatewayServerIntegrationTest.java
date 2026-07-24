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

    @BeforeEach
    void startServer() throws InterruptedException {
        // port 0 = OS tự cấp cổng trống — tránh đụng cổng khi nhiều test chạy song song.
        // AccountFacade/CharacterFacade THẬT với Fake Port (không server-database thật) — test
        // này chỉ verify ping/pong qua socket thật, không chạm Connect/Character, nhưng
        // GatewayServer luôn cần cả 2 Facade để khởi tạo (xem GatewayServer).
        var characterFacade = fakeCharacterFacade();
        var inventoryFacade = fakeInventoryFacade();
        var shopFacade = fakeShopFacade();
        var dialogueFacade = fakeDialogueFacade();
        var questFacade = fakeQuestFacade();
        var friendFacade = fakeFriendFacade();
        var mailFacade = fakeMailFacade();
        var chatFacade = fakeChatFacade();
        var guildFacade = fakeGuildFacade();
        characterRegistry = new ConnectedCharacterRegistry(inventoryFacade, questFacade);
        worldFacade = new WorldFacade(characterRegistry);
        worldFacade.start();
        var positionPersister =
                new CharacterPositionPersister(characterFacade, characterRegistry, Duration.ofMinutes(5));
        var worldGateway = new WorldGateway(worldFacade, characterRegistry, positionPersister);
        var socialFacades = new SocialFacades(friendFacade, mailFacade, chatFacade, guildFacade);
        var contentFacades = new ContentFacades(shopFacade, dialogueFacade, questFacade, socialFacades);
        var facades = new GatewayFacades(fakeAccountFacade(), characterFacade, inventoryFacade, contentFacades);
        server = new GatewayServer(0, facades, worldGateway);
        server.start();
    }

    private static AccountFacade fakeAccountFacade() {
        var accountPort = new AccountPort() {
            private final Map<String, Account> byUsername = new HashMap<>();
            private final AtomicLong nextId = new AtomicLong(1);

            @Override
            public Account findOrCreate(Transaction tx, String username) {
                return byUsername.computeIfAbsent(
                        username, u -> new Account(nextId.getAndIncrement(), u, Instant.now()));
            }
        };
        var sessionPort = new SessionPort() {
            private final Map<String, Long> tokens = new HashMap<>();

            @Override
            public String issue(long accountId) {
                var token = "fake-token-" + accountId;
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
        var useCase = new LoginOrCreateAccountUseCase(accountPort, sessionPort, action -> action.accept(null));
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
                var character = new Character(
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
        var createUseCase = new CreateCharacterUseCase(characterPort, action -> action.accept(null));
        var listUseCase = new ListCharactersUseCase(characterPort);
        var selectUseCase = new SelectCharacterUseCase(characterPort);
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
        var addItemUseCase = new AddItemUseCase(inventoryPort, action -> action.accept(null));
        var grantLootUseCase = new GrantLootUseCase(dropTablePort, inventoryPort, action -> action.accept(null));
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
        var equipItemUseCase = new EquipItemUseCase(
                inventoryPort, itemTemplatePort, equipmentPort, action -> action.accept(null));
        var unequipItemUseCase = new UnequipItemUseCase(equipmentPort, action -> action.accept(null));
        var computeEquipmentBonusUseCase =
                new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort);
        var equipmentUseCases =
                new EquipmentUseCases(equipItemUseCase, unequipItemUseCase, computeEquipmentBonusUseCase);
        // Cường hoá không dùng trong test này — fake tối thiểu để compile.
        GoldPort goldPort = (tx, characterId, amount) -> true;
        AuditPort auditPort = (tx, characterId, action, detailJson) -> { };
        var enhancePorts = new EnhancePorts(inventoryPort, goldPort, auditPort, itemTemplatePort);
        var enhanceItemUseCase = new EnhanceItemUseCase(enhancePorts, action -> action.accept(null));
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
        var ports = new dev.kage.shop.application.ShopPorts(
                catalogPort, shopGoldPort, inventoryGatewayPort, shopAuditPort);
        var listShopItemsUseCase = new dev.kage.shop.application.ListShopItemsUseCase(catalogPort);
        var buyItemUseCase = new dev.kage.shop.application.BuyItemUseCase(ports, action -> action.accept(null));
        var sellItemUseCase = new dev.kage.shop.application.SellItemUseCase(ports, action -> action.accept(null));
        return new dev.kage.shop.api.ShopFacade(listShopItemsUseCase, buyItemUseCase, sellItemUseCase);
    }

    // Dialogue dùng trong 1 test socket thật (NPC không tồn tại) — Fake port luôn trả rỗng, phần
    // còn lại tối thiểu để compile, cùng convention fakeShopFacade().
    private dev.kage.dialogue.api.DialogueFacade fakeDialogueFacade() {
        dev.kage.dialogue.domain.NpcTemplatePort npcTemplatePort = npcCode -> Optional.empty();
        var useCase = new dev.kage.dialogue.application.GetDialogueNodeUseCase(npcTemplatePort);
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

        var acceptQuestUseCase = new dev.kage.quest.application.AcceptQuestUseCase(progressPort, templatePort, tx);
        var processMonsterKilledUseCase =
                new dev.kage.quest.application.ProcessMonsterKilledUseCase(progressPort, templatePort, tx);
        var rewardPorts = new dev.kage.quest.application.QuestRewardPorts(expPort, inventoryGrantPort, auditPort);
        var claimQuestRewardUseCase = new dev.kage.quest.application.ClaimQuestRewardUseCase(
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
        var useCases = new dev.kage.friend.application.FriendUseCases(
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
        dev.kage.mail.domain.TransactionRunner mailTx = action -> action.accept(null);
        var sendMailUseCase = new dev.kage.mail.application.SendMailUseCase(mailRepositoryPort, mailTx);
        var listMailUseCase = new dev.kage.mail.application.ListMailUseCase(mailRepositoryPort);
        var mailPorts = new dev.kage.mail.application.MailPorts(
                mailRepositoryPort, mailGoldPort, mailInventoryGatewayPort, mailAuditPort);
        var claimMailUseCase = new dev.kage.mail.application.ClaimMailUseCase(mailPorts, mailTx);
        var deleteMailUseCase = new dev.kage.mail.application.DeleteMailUseCase(mailRepositoryPort);
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
        var useCases = new dev.kage.guild.application.GuildUseCases(
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
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            long characterId = connectCreateAndSelect(socket, in, "shutdowntest");

            var moveCommand = MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());
            readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE); // xác nhận world đã bắt đầu di chuyển

            Thread.sleep(200); // để vài tick 60Hz chạy — x phải > 0 lúc này nếu đang di chuyển thật

            // MoveCommand thứ 2 (dừng lại) — MoveResponse trả về là vị trí HIỆN TẠI, dùng làm giá
            // trị kỳ vọng để so sánh với DB sau khi tắt server (không đoán khoảng cách đã đi).
            var stopCommand = MoveCommand.newBuilder().setDirX(0f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 11, stopCommand.toByteArray());
            var stopResponse =
                    MoveResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE).payload());
            assertThat(stopResponse.getX()).isGreaterThan(0f); // sanity: di chuyển thật đã xảy ra

            server.stop(); // KHÔNG đợi @AfterEach — test tự gọi để verify NGAY sau khi trả về

            var savedPosition = characterPort.loadPosition(characterId);
            assertThat(savedPosition.x()).isEqualTo(stopResponse.getX());
        }
    }

    @Test
    @Timeout(10)
    void clientShouldReceivePong_overRealSocket() throws Exception {
        try (var socket = new Socket("127.0.0.1", server.port())) {
            var out = new DataOutputStream(socket.getOutputStream());
            var in = new DataInputStream(socket.getInputStream());

            var ping = PingCommand.newBuilder().setClientTimestampMs(999L).build();
            var header = new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, 1);
            byte[] frame = PacketCodec.encodeFrame(header, ping.toByteArray());
            out.writeInt(frame.length);
            out.write(frame);
            out.flush();

            var decoded = readFrame(in);

            assertThat(decoded.header().opcode()).isEqualTo(Opcode.OPCODE_PONG_VALUE);
            assertThat(decoded.header().sequenceId()).isEqualTo(1);

            var pong = PongResponse.parseFrom(decoded.payload());
            assertThat(pong.getClientTimestampMs()).isEqualTo(999L);
        }
    }

    @Test
    @Timeout(10)
    void serverShouldAcceptMultipleConnectionsConcurrently() throws Exception {
        try (var socketA = new Socket("127.0.0.1", server.port());
                var socketB = new Socket("127.0.0.1", server.port())) {
            sendPing(socketA, 1, 111L);
            sendPing(socketB, 1, 222L);

            var pongA = PongResponse.parseFrom(
                    readFrame(new DataInputStream(socketA.getInputStream())).payload());
            var pongB = PongResponse.parseFrom(
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
        try (var socketA = new Socket("127.0.0.1", server.port());
                var socketB = new Socket("127.0.0.1", server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            var inA = new DataInputStream(socketA.getInputStream());
            var inB = new DataInputStream(socketB.getInputStream());

            // Account.username giới hạn 3-20 ký tự — không dùng UUID đầy đủ được.
            var suffix = UUID.randomUUID().toString().substring(0, 8);
            long characterA = connectCreateAndSelect(socketA, inA, "a" + suffix);
            long characterB = connectCreateAndSelect(socketB, inB, "b" + suffix);

            // Cả 2 spawn cùng điểm (0,0) — vào AoI của nhau ngay tick kế tiếp sau khi attach.
            var spawnSeenByA = CharacterSpawnedEvent.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE).payload());
            assertThat(spawnSeenByA.getCharacterId()).isEqualTo(characterB);
            var spawnSeenByB = CharacterSpawnedEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE).payload());
            assertThat(spawnSeenByB.getCharacterId()).isEqualTo(characterA);

            // A di chuyển — chỉ gửi HƯỚNG (movement.proto), server tự nhân tốc độ.
            var moveCommand = MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socketA, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());

            // A tự nhận MoveResponse (reconciliation, CLIENT.md §6) khớp sequenceId đã gửi.
            var moveResponseFrame = readUntilOpcode(inA, Opcode.OPCODE_MOVE_RESPONSE_VALUE);
            assertThat(moveResponseFrame.header().sequenceId()).isEqualTo(10);
            var moveResponse = MoveResponse.parseFrom(moveResponseFrame.payload());
            assertThat(moveResponse.getX()).isGreaterThanOrEqualTo(0f);

            // B nhận CharacterMovedEvent về A (AoI — SERVER.md §6), vị trí đã đổi khỏi (0,0).
            var movedSeenByB = CharacterMovedEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE).payload());
            assertThat(movedSeenByB.getCharacterId()).isEqualTo(characterA);
            assertThat(movedSeenByB.getX()).isGreaterThan(0f);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldDamageMonster_overRealSockets() throws Exception {
        // Verify Phase 4 lát cắt 1 qua chính giao thức dây: di chuyển tới gần quái bù nhìn (spawn
        // cố định (5,5), WorldFacade) → SetTargetCommand → nhận AttackResultEvent thật.
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            long characterId = connectCreateAndSelect(socket, in, "atk" + UUID.randomUUID().toString().substring(0, 6));

            // Nhân vật mới spawn (0,0) — quái ở (5,5), cách ~7.07m, đã trong AoI (15m) nên
            // MonsterSpawnedEvent tới ngay, nhưng CHƯA trong tầm đánh (2m) — phải di chuyển lại gần.
            var monsterSpawned = MonsterSpawnedEvent.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE).payload());
            long monsterId = monsterSpawned.getMonsterId();

            // Hướng chéo (0.7071, 0.7071) — chuẩn hoá, giống hệt client thật gửi (server tự nhân
            // tốc độ, movement.proto). ~1.2s ở 5 m/s đưa nhân vật vào trong tầm đánh (còn cách quái ~1m).
            var moveCommand = MoveCommand.newBuilder().setDirX(0.7071f).setDirY(0.7071f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());
            Thread.sleep(1200);
            var stopCommand = MoveCommand.newBuilder().setDirX(0f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 11, stopCommand.toByteArray());

            var setTarget = SetTargetCommand.newBuilder().setTargetEntityId(monsterId).build();
            sendFrame(socket, Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, 12, setTarget.toByteArray());

            var attackResult = AttackResultEvent.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE).payload());
            assertThat(attackResult.getAttackerEntityId()).isEqualTo(characterId);
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
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "equip" + UUID.randomUUID().toString().substring(0, 6));

            var equipCommand = EquipItemCommand.newBuilder()
                    .setItemId(1L)
                    .setSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON)
                    .build();
            sendFrame(socket, Opcode.OPCODE_EQUIP_ITEM_COMMAND_VALUE, 10, equipCommand.toByteArray());

            var response = EquipItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_EQUIP_ITEM_RESPONSE_VALUE).payload());
            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getErrorKey()).isEqualTo("error.item_not_found");
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveUnequipItemResponse_overRealSockets() throws Exception {
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "unequip" + UUID.randomUUID().toString().substring(0, 6));

            var unequipCommand = UnequipItemCommand.newBuilder().setSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON).build();
            sendFrame(socket, Opcode.OPCODE_UNEQUIP_ITEM_COMMAND_VALUE, 10, unequipCommand.toByteArray());

            var response = UnequipItemResponse.parseFrom(
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
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "enhance" + UUID.randomUUID().toString().substring(0, 6));

            var enhanceCommand = EnhanceItemCommand.newBuilder().setItemId(1L).build();
            sendFrame(socket, Opcode.OPCODE_ENHANCE_ITEM_COMMAND_VALUE, 10, enhanceCommand.toByteArray());

            var response = EnhanceItemResponse.parseFrom(
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
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "shoplist" + UUID.randomUUID().toString().substring(0, 6));

            var request = ListShopItemsRequest.newBuilder().build();
            sendFrame(socket, Opcode.OPCODE_LIST_SHOP_ITEMS_REQUEST_VALUE, 10, request.toByteArray());

            var response = ListShopItemsResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE).payload());
            assertThat(response.getListingsList()).isEmpty();
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveBuyItemResponse_overRealSockets() throws Exception {
        // Cùng lý do clientShouldReceiveEnhanceItemResponse_overRealSockets: catalog rỗng nên
        // itemTemplateId=1 chắc chắn không có trong shop — verify đúng nhánh ITEM_NOT_IN_SHOP.
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "buy" + UUID.randomUUID().toString().substring(0, 6));

            var command = BuyItemCommand.newBuilder().setItemTemplateId(1L).setQuantity(1).build();
            sendFrame(socket, Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, 10, command.toByteArray());

            var response = BuyItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE).payload());
            assertThat(response.getOutcome()).isEqualTo(BuyOutcome.BUY_OUTCOME_ITEM_NOT_IN_SHOP);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveSellItemResponse_overRealSockets() throws Exception {
        // fakeShopFacade().findItem() luôn trả rỗng — verify đúng nhánh ITEM_NOT_FOUND.
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "sell" + UUID.randomUUID().toString().substring(0, 6));

            var command = SellItemCommand.newBuilder().setItemId(1L).setQuantity(1).build();
            sendFrame(socket, Opcode.OPCODE_SELL_ITEM_COMMAND_VALUE, 10, command.toByteArray());

            var response = SellItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE).payload());
            assertThat(response.getOutcome()).isEqualTo(SellOutcome.SELL_OUTCOME_ITEM_NOT_FOUND);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveErrorResponse_whenTalkingToUnknownNpc_overRealSockets() throws Exception {
        // fakeDialogueFacade()'s NpcTemplatePort luôn trả rỗng — verify đúng lỗi 404 (không có
        // opcode DialogueNodeResponse riêng cho nhánh này, sendError dùng chung OPCODE_ERROR).
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "npc" + UUID.randomUUID().toString().substring(0, 6));

            var command = TalkToNpcCommand.newBuilder().setNpcCode("town_elder").build();
            sendFrame(socket, Opcode.OPCODE_TALK_TO_NPC_COMMAND_VALUE, 10, command.toByteArray());

            var response = ErrorResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_ERROR_VALUE).payload());
            assertThat(response.getErrorKey()).isEqualTo("error.npc_not_found");
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveClaimQuestRewardResponse_overRealSockets() throws Exception {
        // fakeQuestFacade()'s QuestTemplatePort luôn trả rỗng — verify đúng nhánh QUEST_NOT_FOUND
        // (kiểm tra template TRƯỚC progress, xem ClaimQuestRewardUseCase.execute).
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "quest" + UUID.randomUUID().toString().substring(0, 6));

            var command = ClaimQuestRewardCommand.newBuilder().setQuestCode("kill_training_dummies").build();
            sendFrame(socket, Opcode.OPCODE_CLAIM_QUEST_REWARD_COMMAND_VALUE, 10, command.toByteArray());

            var response = ClaimQuestRewardResponse.parseFrom(
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
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "friend" + UUID.randomUUID().toString().substring(0, 6));

            var command = SendFriendRequestCommand.newBuilder().setTargetName("khong_ton_tai").build();
            sendFrame(socket, Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE, 10, command.toByteArray());

            var response = SendFriendRequestResponse.parseFrom(
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
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(socket, in, "mail" + UUID.randomUUID().toString().substring(0, 6));

            var command = SendMailCommand.newBuilder()
                    .setRecipientName("khong_ton_tai")
                    .setSubject("s")
                    .setBody("b")
                    .build();
            sendFrame(socket, Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE, 10, command.toByteArray());

            var response =
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
        try (var socketA = new Socket("127.0.0.1", server.port());
                var socketB = new Socket("127.0.0.1", server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            var inA = new DataInputStream(socketA.getInputStream());
            var inB = new DataInputStream(socketB.getInputStream());
            var suffix = UUID.randomUUID().toString().substring(0, 8);
            connectCreateAndSelect(socketA, inA, "wa" + suffix);
            connectCreateAndSelect(socketB, inB, "wb" + suffix);

            var command = SendChatMessageCommand.newBuilder()
                    .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                    .setBody("xin chào thế giới")
                    .build();
            sendFrame(socketA, Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, 10, command.toByteArray());

            var ackResponse = SendChatMessageResponse.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE).payload());
            assertThat(ackResponse.getOutcome()).isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK);

            var eventSeenByA =
                    ChatMessageEvent.parseFrom(readUntilOpcode(inA, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE).payload());
            assertThat(eventSeenByA.getBody()).isEqualTo("xin chào thế giới");
            var eventSeenByB =
                    ChatMessageEvent.parseFrom(readUntilOpcode(inB, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE).payload());
            assertThat(eventSeenByB.getBody()).isEqualTo("xin chào thế giới");
        }
    }

    private static long connectCreateAndSelect(Socket socket, DataInputStream in, String username)
            throws Exception {
        var connectRequest = ConnectRequest.newBuilder().setUsername(username).build();
        sendFrame(socket, Opcode.OPCODE_CONNECT_REQUEST_VALUE, 1, connectRequest.toByteArray());
        ConnectResponse.parseFrom(readFrame(in).payload());

        // Character.validateName: chỉ chữ/số, KHÔNG dấu gạch ngang (feature-character/CLAUDE.md).
        var createRequest = CreateCharacterRequest.newBuilder()
                .setName("Hero" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        sendFrame(socket, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 2, createRequest.toByteArray());
        var createResponse = CreateCharacterResponse.parseFrom(readFrame(in).payload());
        long characterId = createResponse.getCharacter().getId();

        var selectRequest = SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        sendFrame(socket, Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, 3, selectRequest.toByteArray());
        readFrame(in); // SelectCharacterResponse — nội dung đã verify ở GatewayPipelineTest

        return characterId;
    }

    private static void sendFrame(Socket socket, int opcode, int sequenceId, byte[] payload) throws Exception {
        var out = new DataOutputStream(socket.getOutputStream());
        var header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        byte[] frame = PacketCodec.encodeFrame(header, payload);
        out.writeInt(frame.length);
        out.write(frame);
        out.flush();
    }

    /** AoI Event tới bất kỳ lúc nào giữa các Response khác — đọc tới khi thấy đúng opcode cần. */
    private static PacketCodec.Frame readUntilOpcode(DataInputStream in, int expectedOpcode) throws Exception {
        for (int attempt = 0; attempt < 1000; attempt++) {
            var frame = readFrame(in);
            if (frame.header().opcode() == expectedOpcode) {
                return frame;
            }
        }
        throw new AssertionError("Không thấy opcode " + expectedOpcode + " sau 1000 frame");
    }

    private static void sendPing(Socket socket, int sequenceId, long clientTimestampMs) throws Exception {
        var out = new DataOutputStream(socket.getOutputStream());
        var ping = PingCommand.newBuilder().setClientTimestampMs(clientTimestampMs).build();
        var header = new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, sequenceId);
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
