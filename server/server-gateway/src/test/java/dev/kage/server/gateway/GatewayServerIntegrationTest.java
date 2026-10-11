package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.account.api.AccountFacade;
import dev.kage.account.application.LoginOrCreateAccountUseCase;
import dev.kage.account.domain.Account;
import dev.kage.account.domain.AccountPort;
import dev.kage.account.domain.SessionPort;
import dev.kage.account.domain.Transaction;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CharacterUseCases;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.application.SelectFactionUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.Faction;
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
import dev.kage.protocol.AcceptDuelCommand;
import dev.kage.protocol.AcceptDuelOutcome;
import dev.kage.protocol.AcceptDuelResponse;
import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.BuyItemCommand;
import dev.kage.protocol.BuyItemResponse;
import dev.kage.protocol.BuyOutcome;
import dev.kage.protocol.ChallengeDuelCommand;
import dev.kage.protocol.ChallengeDuelOutcome;
import dev.kage.protocol.ChallengeDuelResponse;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.ClaimQuestRewardCommand;
import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.ConfirmTradeCommand;
import dev.kage.protocol.ConfirmTradeOutcome;
import dev.kage.protocol.ConfirmTradeResponse;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.DeclineDuelCommand;
import dev.kage.protocol.DeclineDuelOutcome;
import dev.kage.protocol.DeclineDuelResponse;
import dev.kage.protocol.DuelChallengeDeclinedEvent;
import dev.kage.protocol.DuelChallengeReceivedEvent;
import dev.kage.protocol.DuelResultEvent;
import dev.kage.protocol.DuelStartedEvent;
import dev.kage.protocol.EnhanceItemCommand;
import dev.kage.protocol.EnhanceItemResponse;
import dev.kage.protocol.EnhanceOutcome;
import dev.kage.protocol.EnterDungeonCommand;
import dev.kage.protocol.EnterDungeonOutcome;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.EquipItemCommand;
import dev.kage.protocol.EquipItemResponse;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ExitDuelCommand;
import dev.kage.protocol.ExitDuelOutcome;
import dev.kage.protocol.ExitDuelResponse;
import dev.kage.protocol.ExitDungeonCommand;
import dev.kage.protocol.ExitDungeonOutcome;
import dev.kage.protocol.ExitDungeonResponse;
import dev.kage.protocol.GetPvpRatingRequest;
import dev.kage.protocol.GetPvpRatingResponse;
import dev.kage.protocol.JumpCommand;
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
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageOutcome;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.SendFriendRequestCommand;
import dev.kage.protocol.SendFriendRequestOutcome;
import dev.kage.protocol.SendFriendRequestResponse;
import dev.kage.protocol.SendMailCommand;
import dev.kage.protocol.SendMailOutcome;
import dev.kage.protocol.SendMailResponse;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.TalkToNpcCommand;
import dev.kage.protocol.UnequipItemCommand;
import dev.kage.protocol.UnequipItemResponse;
import dev.kage.protocol.UseItemCommand;
import dev.kage.protocol.UseItemResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldFacade;

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
    private MainWorldChannelRegistry mainWorldChannels;
    private CharacterPort characterPort;
    private ConnectedCharacterRegistry characterRegistry;
    private dev.kage.party.api.PartyFacade partyFacade;
    private dev.kage.pvp.api.PvpFacade pvpFacade;

    @BeforeEach
    void startServer() throws InterruptedException, javax.net.ssl.SSLException {
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
        pvpFacade = fakePvpFacade();
        io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        GatewayNetworkMetrics networkMetrics = new GatewayNetworkMetrics(meterRegistry);
        GatewayObservability observability = new GatewayObservability(networkMetrics, GatewayTracing.disabled());
        characterRegistry = new ConnectedCharacterRegistry(
                inventoryFacade, questFacade, ExperienceGrantPort.disabled(), networkMetrics);
        WorldFacade firstChannelFacade = new WorldFacade(
                characterRegistry, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        firstChannelFacade.start();
        mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry, firstChannelFacade, meterRegistry, TestWorldContents.trainingGroundWithElder());
        CharacterPositionPersister positionPersister =
                new CharacterPositionPersister(characterFacade, characterRegistry, Duration.ofMinutes(5));
        DungeonInstanceRegistry dungeonInstanceRegistry = new DungeonInstanceRegistry(
                characterRegistry,
                new DungeonInstanceRegistry.DungeonFacades(characterFacade, partyFacade),
                mainWorldChannels,
                TestWorldContents.withBoss());
        ArenaInstanceRegistry arenaInstanceRegistry =
                new ArenaInstanceRegistry(characterRegistry, characterFacade, pvpFacade, mainWorldChannels);
        WorldGateway worldGateway = new WorldGateway(
                mainWorldChannels,
                characterRegistry,
                positionPersister,
                dungeonInstanceRegistry,
                arenaInstanceRegistry);
        SocialFacades socialFacades = new SocialFacades(
                friendFacade, mailFacade, chatFacade, guildFacade, partyFacade, tradeFacade, pvpFacade);
        ContentFacades contentFacades = new ContentFacades(shopFacade, dialogueFacade, questFacade, socialFacades);
        GatewayFacades facades =
                new GatewayFacades(fakeAccountFacade(), characterFacade, inventoryFacade, contentFacades);
        // TlsConfig.disabled() — mọi test ở trên verify giao thức qua socket thuần, TLS thật
        // verify riêng ở cuối file này (shouldReceivePong_overTlsSocket_withPinnedCert và 2 test
        // cạnh nó, ADR-027) — tự start/stop server RIÊNG, không dùng field server này.
        server = new GatewayServer(
                0, facades, new GatewayServer.GatewayContext(worldGateway, observability), TlsConfig.disabled());
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
            public long loadGold(long characterId) {
                return 0L; // test này không kiểm tra vàng — nhân vật mới vốn có 0 (V7 DEFAULT 0)
            }

            @Override
            public List<Character> listByAccount(long accountId) {
                return characters.stream()
                        .filter(c -> c.accountId() == accountId)
                        .toList();
            }

            @Override
            public int countByAccount(long accountId) {
                return listByAccount(accountId).size();
            }

            @Override
            public int lockAccountAndCountCharacters(dev.kage.character.domain.Transaction tx, long accountId) {
                return countByAccount(accountId);
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

            @Override
            public long addExperience(long characterId, long amount) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            // Thi hành thật (không ném) từ ADR-045: test đổi hệ phái cần nhân vật đạt Lv10, và
            // trong file này không có đường lên cấp nào khác — giết quái tới cấp 10 qua socket
            // thật sẽ mất hàng chục giây cho một thứ không phải chủ đề của test.
            @Override
            public boolean saveLevel(long characterId, int level, long derivedFromExp) {
                if (findById(characterId).map(Character::exp).orElse(-1L) != derivedFromExp) {
                    return false;
                }
                replaceCharacter(
                        characterId,
                        c -> new Character(
                                c.id(),
                                c.accountId(),
                                c.realmId(),
                                c.name(),
                                c.faction(),
                                level,
                                c.exp(),
                                c.rebirthCount(),
                                c.createdAt()));
                return true;
            }

            @Override
            public void updateFaction(long characterId, Faction faction) {
                replaceCharacter(
                        characterId,
                        c -> new Character(
                                c.id(),
                                c.accountId(),
                                c.realmId(),
                                c.name(),
                                faction,
                                c.level(),
                                c.exp(),
                                c.rebirthCount(),
                                c.createdAt()));
            }

            private void replaceCharacter(long characterId, UnaryOperator<Character> update) {
                for (int i = 0; i < characters.size(); i++) {
                    Character existing = characters.get(i);
                    if (existing.id() == characterId) {
                        characters.set(i, update.apply(existing));
                        return;
                    }
                }
            }
        };
        CreateCharacterUseCase createUseCase = new CreateCharacterUseCase(characterPort, action -> action.accept(null));
        ListCharactersUseCase listUseCase = new ListCharactersUseCase(characterPort);
        SelectCharacterUseCase selectUseCase = new SelectCharacterUseCase(characterPort);
        SelectFactionUseCase selectFactionUseCase = new SelectFactionUseCase(characterPort);
        CharacterUseCases useCases = new CharacterUseCases(
                createUseCase,
                listUseCase,
                selectUseCase,
                selectFactionUseCase,
                new GrantExperienceUseCase(characterPort));
        return new CharacterFacade(useCases, characterPort);
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
        AddItemUseCase addItemUseCase =
                new AddItemUseCase(inventoryPort, itemTemplatePort, action -> action.accept(null));
        GrantLootUseCase grantLootUseCase =
                new GrantLootUseCase(dropTablePort, inventoryPort, itemTemplatePort, action -> action.accept(null));
        // Equip/unequip không dùng trong test này — fake tối thiểu để compile.
        EquipmentPort equipmentPort = new EquipmentPort() {
            @Override
            public Optional<Equipment> findByCharacter(long characterId) {
                return Optional.of(new Equipment(characterId, Map.of()));
            }

            @Override
            public void save(dev.kage.inventory.domain.Transaction tx, Equipment equipment) {}
        };
        EquipItemUseCase equipItemUseCase =
                new EquipItemUseCase(inventoryPort, itemTemplatePort, equipmentPort, action -> action.accept(null));
        UnequipItemUseCase unequipItemUseCase = new UnequipItemUseCase(equipmentPort, action -> action.accept(null));
        ComputeEquipmentBonusUseCase computeEquipmentBonusUseCase =
                new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort);
        EquipmentUseCases equipmentUseCases =
                new EquipmentUseCases(equipItemUseCase, unequipItemUseCase, computeEquipmentBonusUseCase);
        // Cường hoá không dùng trong test này — fake tối thiểu để compile.
        GoldPort goldPort = (tx, characterId, amount) -> true;
        AuditPort auditPort = (tx, characterId, action, detailJson) -> {};
        dev.kage.inventory.domain.IdempotencyPort idempotencyPort = (tx, idempotencyKey) -> {};
        EnhancePorts enhancePorts = new EnhancePorts(
                inventoryPort, goldPort, auditPort, itemTemplatePort, idempotencyPort, (tx, c, i) -> false);
        EnhanceItemUseCase enhanceItemUseCase = new EnhanceItemUseCase(enhancePorts, action -> action.accept(null));
        return new InventoryFacade(
                new dev.kage.inventory.api.ItemAcquisitionUseCases(addItemUseCase, grantLootUseCase),
                equipmentUseCases,
                new dev.kage.inventory.api.ItemActionUseCases(
                        enhanceItemUseCase,
                        new dev.kage.inventory.application.UseItemUseCase(
                                inventoryPort, itemTemplatePort, auditPort, action -> action.accept(null))),
                new dev.kage.inventory.api.InventoryQueryUseCases(
                        new dev.kage.inventory.application.ListInventoryUseCase(inventoryPort, equipmentPort),
                        new dev.kage.inventory.application.GetItemCatalogUseCase(itemTemplatePort)));
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
            public void creditGold(dev.kage.shop.domain.Transaction tx, long characterId, long amount) {}
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
        dev.kage.shop.domain.AuditPort shopAuditPort = (tx, characterId, action, detailJson) -> {};
        dev.kage.shop.domain.IdempotencyPort shopIdempotencyPort = (tx, idempotencyKey) -> {};
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
            public void save(dev.kage.quest.domain.Transaction tx, dev.kage.quest.domain.QuestProgress progress) {}
        };
        dev.kage.quest.domain.QuestTemplatePort templatePort = questCode -> Optional.empty();
        dev.kage.quest.domain.ExpPort expPort = (tx, characterId, amount) -> {};
        dev.kage.quest.domain.QuestInventoryGrantPort inventoryGrantPort =
                (tx, characterId, itemTemplateCode, quantity) -> true;
        dev.kage.quest.domain.AuditPort auditPort = (tx, characterId, action, detailJson) -> {};
        dev.kage.quest.domain.TransactionRunner tx = action -> action.accept(null);

        dev.kage.quest.application.AcceptQuestUseCase acceptQuestUseCase =
                new dev.kage.quest.application.AcceptQuestUseCase(progressPort, templatePort, tx);
        dev.kage.quest.application.ProcessQuestEventUseCase processQuestEventUseCase =
                new dev.kage.quest.application.ProcessQuestEventUseCase(
                        progressPort, (t, characterId, itemTemplateCode, quantity) -> false, auditPort, tx);
        dev.kage.quest.domain.IdempotencyPort questIdempotencyPort = (t, idempotencyKey) -> {};
        dev.kage.quest.application.QuestRewardPorts rewardPorts = new dev.kage.quest.application.QuestRewardPorts(
                expPort, inventoryGrantPort, auditPort, questIdempotencyPort);
        dev.kage.quest.application.ClaimQuestRewardUseCase claimQuestRewardUseCase =
                new dev.kage.quest.application.ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, tx);
        return new dev.kage.quest.api.QuestFacade(
                acceptQuestUseCase,
                processQuestEventUseCase,
                claimQuestRewardUseCase,
                new dev.kage.quest.application.ListQuestsUseCase(progressPort));
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
        dev.kage.mail.domain.AuditPort mailAuditPort = (tx, characterId, action, detailJson) -> {};
        dev.kage.mail.domain.IdempotencyPort mailIdempotencyPort = (tx, idempotencyKey) -> {};
        dev.kage.mail.domain.TransactionRunner mailTx = action -> action.accept(null);
        dev.kage.mail.application.ListMailUseCase listMailUseCase =
                new dev.kage.mail.application.ListMailUseCase(mailRepositoryPort);
        dev.kage.mail.application.MailPorts mailPorts = new dev.kage.mail.application.MailPorts(
                mailRepositoryPort,
                new FakeMailGoldPort(),
                new FakeMailInventoryGatewayPort(),
                mailAuditPort,
                mailIdempotencyPort);
        dev.kage.mail.application.SendMailUseCase sendMailUseCase =
                new dev.kage.mail.application.SendMailUseCase(mailPorts, mailTx);
        dev.kage.mail.application.ClaimMailUseCase claimMailUseCase =
                new dev.kage.mail.application.ClaimMailUseCase(mailPorts, mailTx);
        dev.kage.mail.application.DeleteMailUseCase deleteMailUseCase =
                new dev.kage.mail.application.DeleteMailUseCase(mailRepositoryPort);
        return new dev.kage.mail.api.MailFacade(sendMailUseCase, listMailUseCase, claimMailUseCase, deleteMailUseCase);
    }

    // Chat không dùng trong test này — fake tối thiểu để compile (cùng convention fakeMailFacade).
    private dev.kage.chat.api.ChatFacade fakeChatFacade() {
        dev.kage.chat.domain.ChatAuditPort chatAuditPort = (characterId, action, detailJson) -> {};
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
            public boolean transferLeadership(long leaderCharacterId, long newLeaderCharacterId) {
                return false; // fake không có nhóm nào để chuyển quyền
            }

            @Override
            public int deleteInvitesInvitedBefore(java.time.Instant cutoff) {
                return 0; // fake không giữ lời mời nào để hết hạn
            }

            @Override
            public dev.kage.party.domain.PartyInvite invite(long partyId, long inviteeCharacterId) {
                long inviteId = nextInviteId.getAndIncrement();
                dev.kage.party.domain.PartyInvite invite =
                        new dev.kage.party.domain.PartyInvite(inviteId, partyId, inviteeCharacterId, Instant.now());
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
                membersByPartyId
                        .computeIfAbsent(invite.partyId(), id -> new ArrayList<>())
                        .add(member);
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
                new dev.kage.party.application.FindMyPartyUseCase(partyPort),
                new dev.kage.party.application.HandOverPartyLeadershipUseCase(partyPort));
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
            public boolean replaceOffer(
                    long sessionId,
                    long characterId,
                    long goldAmount,
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
        dev.kage.trade.domain.TradeAuditPort auditPort = (tx, characterId, action, detailJson) -> {};
        dev.kage.trade.domain.IdempotencyPort idempotencyPort = (tx, idempotencyKey) -> {};
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

    // Fake THẬT (không throw) — test Arena ở file này verify trọn vẹn luồng thách đấu/rating thật
    // qua socket, khác fakeTradeFacade (Trade không test ở đây, chỉ cần compile).
    private dev.kage.pvp.api.PvpFacade fakePvpFacade() {
        dev.kage.pvp.domain.PvpRatingPort ratingPort = new dev.kage.pvp.domain.PvpRatingPort() {
            private final Map<Long, dev.kage.pvp.domain.PvpRating> ratingsByCharacterId = new HashMap<>();

            @Override
            public Optional<dev.kage.pvp.domain.PvpRating> findByCharacterId(long characterId) {
                return Optional.ofNullable(ratingsByCharacterId.get(characterId));
            }

            @Override
            public synchronized dev.kage.pvp.domain.EloRating.Result recordDuelResult(
                    long winnerCharacterId, long loserCharacterId, EloComputer computer) {
                int winnerRating = currentRating(winnerCharacterId);
                int loserRating = currentRating(loserCharacterId);
                dev.kage.pvp.domain.EloRating.Result result = computer.compute(winnerRating, loserRating);
                upsert(winnerCharacterId, result.newWinnerRating(), 1, 0);
                upsert(loserCharacterId, result.newLoserRating(), 0, 1);
                return result;
            }

            private int currentRating(long characterId) {
                return findByCharacterId(characterId)
                        .map(dev.kage.pvp.domain.PvpRating::rating)
                        .orElse(dev.kage.pvp.domain.EloRating.DEFAULT_RATING);
            }

            private void upsert(long characterId, int newRating, int winsDelta, int lossesDelta) {
                dev.kage.pvp.domain.PvpRating existing = ratingsByCharacterId.get(characterId);
                int wins = (existing == null ? 0 : existing.wins()) + winsDelta;
                int losses = (existing == null ? 0 : existing.losses()) + lossesDelta;
                ratingsByCharacterId.put(
                        characterId,
                        new dev.kage.pvp.domain.PvpRating(characterId, newRating, wins, losses, Instant.now()));
            }
        };
        return new dev.kage.pvp.api.PvpFacade(ratingPort);
    }

    @AfterEach
    void stopServer() {
        server.stop();
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    @Test
    @Timeout(15)
    void shouldSavePosition_whenServerShutsDownWhileClientConnected() throws Exception {
        // PHÁT HIỆN THẬT (xem javadoc GatewayServer#stop): bản trước tắt authExecutor TRƯỚC khi
        // đóng channel — channelInactive lúc shutdown bị RejectedExecutionException âm thầm, mất
        // vị trí cuối. Test này verify trực tiếp qua Fake Port, không chỉ đọc code.
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            long characterId = connectCreateAndSelect(socket, in, "shutdowntest");

            MoveCommand moveCommand =
                    MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());
            readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE); // xác nhận world đã bắt đầu di chuyển

            Thread.sleep(200); // để vài tick 60Hz chạy — x phải > 0 lúc này nếu đang di chuyển thật

            // MoveCommand thứ 2 (dừng lại) — MoveResponse trả về là vị trí HIỆN TẠI, dùng làm giá
            // trị kỳ vọng để so sánh với DB sau khi tắt server (không đoán khoảng cách đã đi).
            MoveCommand stopCommand =
                    MoveCommand.newBuilder().setDirX(0f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 11, stopCommand.toByteArray());
            MoveResponse stopResponse = MoveResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE).payload());
            assertThat(stopResponse.getX()).isGreaterThan(0f); // sanity: di chuyển thật đã xảy ra

            server.stop(); // KHÔNG đợi @AfterEach — test tự gọi để verify NGAY sau khi trả về

            PositionSnapshot savedPosition = characterPort.loadPosition(characterId);
            assertThat(savedPosition.x()).isEqualTo(stopResponse.getX());
        }
    }

    @Test
    @Timeout(10)
    void clientShouldReceivePong_overRealSocket() throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            PingCommand ping =
                    PingCommand.newBuilder().setClientTimestampMs(999L).build();
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
        try (Socket socketA = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socketB = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
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
    void observerShouldGetNewFaction_whenAnotherCharacterChangesItInPlace() throws Exception {
        // ADR-045 — nửa CÒN LẠI của bug "đổi hệ phái xong, người đang đứng cạnh vẫn thấy model cũ".
        // Nửa trước (2026-08-29) chỉ vá cache ngoại hình nên chỉ người tới SAU mới thấy đúng; đây
        // là đường đi thật của nửa sau, qua chính giao thức dây: A đứng nhìn, B đổi hệ phái, A phải
        // nhận opcode 307 mang hệ phái MỚI mà không cần B rời rồi vào lại tầm nhìn.
        try (Socket socketA = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socketB = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            DataInputStream inB = new DataInputStream(socketB.getInputStream());

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            long characterA = connectCreateAndSelect(socketA, inA, "c" + suffix);
            long characterB = connectCreateAndSelect(socketB, inB, "d" + suffix);

            // Cùng spawn ở (0,0) — phải THẤY NHAU trước đã, nếu không thì test chỉ đang chứng minh
            // "không ai quan sát thì không ai nhận gì".
            CharacterSpawnedEvent spawnSeenByA =
                    CharacterSpawnedEvent.parseFrom(readUntilOpcode(inA, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                            .payload());
            assertThat(spawnSeenByA.getCharacterId()).isEqualTo(characterB);
            assertThat(spawnSeenByA.getFaction()).isEqualTo(dev.kage.protocol.Faction.FACTION_HOA);
            CharacterSpawnedEvent spawnSeenByB =
                    CharacterSpawnedEvent.parseFrom(readUntilOpcode(inB, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                            .payload());
            assertThat(spawnSeenByB.getCharacterId()).isEqualTo(characterA);

            // Đổi hệ phái đòi Lv10 (SelectFactionUseCase) và file này không có đường lên cấp nào —
            // seed thẳng vào Port, đúng cách GatewayPipelineTest đã làm cho hai test hệ phái ở đó.
            characterPort.saveLevel(
                    characterB,
                    10,
                    characterPort.findById(characterB).orElseThrow().exp());
            dev.kage.protocol.SelectFactionRequest factionRequest = dev.kage.protocol.SelectFactionRequest.newBuilder()
                    .setFaction(dev.kage.protocol.Faction.FACTION_THUY)
                    .build();
            sendFrame(socketB, Opcode.OPCODE_SELECT_FACTION_REQUEST_VALUE, 20, factionRequest.toByteArray());
            assertThat(dev.kage.protocol.SelectFactionResponse.parseFrom(
                                    readUntilOpcode(inB, Opcode.OPCODE_SELECT_FACTION_RESPONSE_VALUE)
                                            .payload())
                            .getSuccess())
                    .isTrue();

            dev.kage.protocol.CharacterAppearanceChangedEvent seenByA =
                    dev.kage.protocol.CharacterAppearanceChangedEvent.parseFrom(
                            readUntilOpcode(inA, Opcode.OPCODE_CHARACTER_APPEARANCE_CHANGED_EVENT_VALUE)
                                    .payload());
            assertThat(seenByA.getCharacterId()).isEqualTo(characterB);
            assertThat(seenByA.getFaction()).isEqualTo(dev.kage.protocol.Faction.FACTION_THUY);
        }
    }

    @Test
    @Timeout(15)
    void twoClientsShouldSeeEachOtherMove_overRealSockets() throws Exception {
        // Verify đúng Phase 3 Exit Criteria (ROADMAP.md): "2 client kết nối cùng server, thấy
        // nhau di chuyển" — qua chính giao thức dây (socket thật), không chỉ nội bộ WorldFacade.
        try (Socket socketA = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socketB = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            DataInputStream inB = new DataInputStream(socketB.getInputStream());

            // Account.username giới hạn 3-20 ký tự — không dùng UUID đầy đủ được.
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            long characterA = connectCreateAndSelect(socketA, inA, "a" + suffix);
            long characterB = connectCreateAndSelect(socketB, inB, "b" + suffix);

            // Cả 2 spawn cùng điểm (0,0) — vào AoI của nhau ngay tick kế tiếp sau khi attach.
            CharacterSpawnedEvent spawnSeenByA =
                    CharacterSpawnedEvent.parseFrom(readUntilOpcode(inA, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                            .payload());
            assertThat(spawnSeenByA.getCharacterId()).isEqualTo(characterB);
            CharacterSpawnedEvent spawnSeenByB =
                    CharacterSpawnedEvent.parseFrom(readUntilOpcode(inB, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                            .payload());
            assertThat(spawnSeenByB.getCharacterId()).isEqualTo(characterA);

            // A di chuyển — chỉ gửi HƯỚNG (movement.proto), server tự nhân tốc độ.
            MoveCommand moveCommand =
                    MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socketA, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());

            // A tự nhận MoveResponse (reconciliation, CLIENT.md §6) khớp sequenceId đã gửi.
            PacketCodec.Frame moveResponseFrame = readUntilOpcode(inA, Opcode.OPCODE_MOVE_RESPONSE_VALUE);
            assertThat(moveResponseFrame.header().sequenceId()).isEqualTo(10);
            MoveResponse moveResponse = MoveResponse.parseFrom(moveResponseFrame.payload());
            assertThat(moveResponse.getX()).isGreaterThanOrEqualTo(0f);

            // B nhận CharacterMovedEvent về A (AoI — SERVER.md §6), vị trí đã đổi khỏi (0,0).
            CharacterMovedEvent movedSeenByB =
                    CharacterMovedEvent.parseFrom(readUntilOpcode(inB, Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE)
                            .payload());
            assertThat(movedSeenByB.getCharacterId()).isEqualTo(characterA);
            assertThat(movedSeenByB.getX()).isGreaterThan(0f);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldBecomeAirborne_whenSendingJumpCommand_overRealSockets() throws Exception {
        // ADR-033 — verify đúng giao thức dây (JumpCommand rỗng -> MoveResponse.grounded=false),
        // không chỉ nội bộ WorldFacade (đã có WorldFacadeTest.shouldApplyJumpVelocity_...).
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            connectCreateAndSelect(socket, in, "j" + suffix);

            JumpCommand jumpCommand = JumpCommand.newBuilder().build();
            sendFrame(socket, Opcode.OPCODE_JUMP_COMMAND_VALUE, 11, jumpCommand.toByteArray());

            PacketCodec.Frame jumpResponseFrame = readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE);
            assertThat(jumpResponseFrame.header().sequenceId()).isEqualTo(11);
            MoveResponse jumpResponse = MoveResponse.parseFrom(jumpResponseFrame.payload());
            assertThat(jumpResponse.getGrounded()).isFalse();
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
        try (Socket socketA = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socketB = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socketC = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            socketC.setSoTimeout(5000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            DataInputStream inB = new DataInputStream(socketB.getInputStream());
            DataInputStream inC = new DataInputStream(socketC.getInputStream());

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            long characterA = connectCreateAndSelect(socketA, inA, "da" + suffix);
            long characterB = connectCreateAndSelect(socketB, inB, "db" + suffix);
            connectCreateAndSelect(socketC, inC, "dc" + suffix);

            // AoI sync() chạy 20Hz (tách khỏi tick 60Hz, WorldConstants.AOI_SYNC_RATE_HZ) — PHẢI
            // đợi C thật sự THẤY A/B spawn (bằng chứng sync() đã chạy ít nhất 1 lần từ lúc C attach)
            // trước khi cho A/B rời world chính, nếu không assertion "C thấy A/B despawn" bên dưới
            // có thể đợi mãi: nếu sync() CHƯA từng chạy, {@code visibleTo} của A/B vẫn RỖNG lúc
            // detach nên KHÔNG có ai để báo despawn (không phải lỗi despawn, mà chưa từng "spawn").
            java.util.HashSet<Long> spawnedSeenByC = new java.util.HashSet<Long>();
            spawnedSeenByC.add(
                    CharacterSpawnedEvent.parseFrom(readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                                    .payload())
                            .getCharacterId());
            spawnedSeenByC.add(
                    CharacterSpawnedEvent.parseFrom(readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                                    .payload())
                            .getCharacterId());
            assertThat(spawnedSeenByC).containsExactlyInAnyOrder(characterA, characterB);

            // Party A+B qua PartyFacade trực tiếp (đã có test riêng cho luồng mời/chấp nhận qua
            // socket ở feature-party — test này chỉ cần TIỀN ĐIỀU KIỆN "A+B cùng party" để tập
            // trung verify cơ chế dungeon). C KHÔNG vào party — ở lại world chính làm chứng nhân.
            partyFacade.createParty(characterA);
            partyFacade.invite(characterA, characterB);
            long inviteId = partyFacade.listPendingInvites(characterB).get(0).inviteId();
            partyFacade.acceptInvite(inviteId, characterB);

            // A gửi EnterDungeonCommand — kéo THEO CẢ B (cùng party, đang online) vào CHUNG 1 instance.
            EnterDungeonCommand enterCommand = EnterDungeonCommand.newBuilder()
                    .setDungeonCode("sample_dungeon")
                    .build();
            sendFrame(socketA, Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, 20, enterCommand.toByteArray());
            EnterDungeonResponse enterResponseA =
                    EnterDungeonResponse.parseFrom(readUntilOpcode(inA, Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE)
                            .payload());
            assertThat(enterResponseA.getOutcome()).isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);

            // B gửi lại CŨNG EnterDungeonCommand — đã bị A kéo vào sẵn, outcome phải là ALREADY_IN_DUNGEON.
            sendFrame(socketB, Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, 20, enterCommand.toByteArray());
            EnterDungeonResponse enterResponseB =
                    EnterDungeonResponse.parseFrom(readUntilOpcode(inB, Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE)
                            .payload());
            assertThat(enterResponseB.getOutcome())
                    .isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_ALREADY_IN_DUNGEON);

            // C (world chính) phải thấy CẢ A lẫn B despawn — chứng minh 2 người đã RỜI world chính
            // thật (không chỉ "coi như" rời), vì AoI world chính không còn giữ entity của họ nữa.
            java.util.HashSet<Long> despawnedIds = new java.util.HashSet<Long>();
            despawnedIds.add(CharacterDespawnedEvent.parseFrom(
                            readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE)
                                    .payload())
                    .getCharacterId());
            despawnedIds.add(CharacterDespawnedEvent.parseFrom(
                            readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE)
                                    .payload())
                    .getCharacterId());
            assertThat(despawnedIds).containsExactlyInAnyOrder(characterA, characterB);

            // A di chuyển TRONG dungeon — B (cùng instance) phải thấy, chứng minh 2 người đang
            // tick chung 1 World riêng (không phải world chính, đã verify despawn ở trên).
            MoveCommand moveCommand =
                    MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socketA, Opcode.OPCODE_MOVE_COMMAND_VALUE, 21, moveCommand.toByteArray());
            PacketCodec.Frame moveResponseFrame = readUntilOpcode(inA, Opcode.OPCODE_MOVE_RESPONSE_VALUE);
            assertThat(moveResponseFrame.header().sequenceId()).isEqualTo(21);

            CharacterMovedEvent movedSeenByB =
                    CharacterMovedEvent.parseFrom(readUntilOpcode(inB, Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE)
                            .payload());
            assertThat(movedSeenByB.getCharacterId()).isEqualTo(characterA);
            assertThat(movedSeenByB.getX()).isGreaterThan(0f);

            // A rời dungeon — về ĐÚNG vị trí thật trước khi vào (0,0), C (world chính) thấy A
            // spawn lại — chứng minh A đã quay về world chính thành công.
            ExitDungeonCommand exitCommand = ExitDungeonCommand.newBuilder().build();
            sendFrame(socketA, Opcode.OPCODE_EXIT_DUNGEON_COMMAND_VALUE, 22, exitCommand.toByteArray());
            ExitDungeonResponse exitResponseA =
                    ExitDungeonResponse.parseFrom(readUntilOpcode(inA, Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE)
                            .payload());
            assertThat(exitResponseA.getOutcome()).isEqualTo(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK);

            CharacterSpawnedEvent spawnSeenByC =
                    CharacterSpawnedEvent.parseFrom(readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                            .payload());
            assertThat(spawnSeenByC.getCharacterId()).isEqualTo(characterA);
            assertThat(spawnSeenByC.getX()).isEqualTo(0f);
            assertThat(spawnSeenByC.getY()).isEqualTo(0f);

            // B rời nốt — dungeon instance rỗng, World của nó tự dừng (DungeonInstanceRegistry).
            sendFrame(socketB, Opcode.OPCODE_EXIT_DUNGEON_COMMAND_VALUE, 22, exitCommand.toByteArray());
            ExitDungeonResponse exitResponseB =
                    ExitDungeonResponse.parseFrom(readUntilOpcode(inB, Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE)
                            .payload());
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
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(10000);
            DataInputStream in = new DataInputStream(socket.getInputStream());

            long characterId = connectCreateAndSelect(
                    socket, in, "boss" + UUID.randomUUID().toString().substring(0, 6));
            partyFacade.createParty(characterId); // party 1 người — đủ điều kiện vào dungeon.

            EnterDungeonCommand enterCommand = EnterDungeonCommand.newBuilder()
                    .setDungeonCode("sample_dungeon")
                    .build();
            sendFrame(socket, Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, 30, enterCommand.toByteArray());
            EnterDungeonResponse enterResponse =
                    EnterDungeonResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE)
                            .payload());
            assertThat(enterResponse.getOutcome()).isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);

            MonsterSpawnedEvent bossSpawned =
                    MonsterSpawnedEvent.parseFrom(readUntilOpcode(in, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE)
                            .payload());
            assertThat(bossSpawned.getMonsterCode()).isEqualTo("sample_boss");
            long bossId = bossSpawned.getMonsterId();

            SetTargetCommand setTarget =
                    SetTargetCommand.newBuilder().setTargetEntityId(bossId).build();
            sendFrame(socket, Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, 31, setTarget.toByteArray());
            startAttackSpammer(socket); // đánh thường phải bấm tay từ 2026-08-09

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
            assertThat(sawSpecialAction)
                    .as("boss phải dùng heavy_strike hoặc enrage trước khi chết")
                    .isTrue();
        }
    }

    /**
     * Người bị bạn cùng nhóm KÉO vào hầm ngục phải được báo — nợ ghi từ bàn giao 2026-08-30.
     *
     * <p>{@code DungeonInstanceRegistry.enterDungeon} dời MỌI thành viên đang online sang World hầm
     * ngục, nhưng {@code handleEnterDungeonBlocking} chỉ trả {@code EnterDungeonResponse} cho NGƯỜI
     * GỬI. Test này chứng minh phía server đã dời B thật (B nhận spawn của boss từ World hầm ngục)
     * mà B không nhận một gói nào thuộc dải Dungeon (1500-1599) — nên client của B không biết mình
     * đang ở đâu (không dải chữ HUD, không dọn thực thể của làng, vị trí sai tới lần đi đầu tiên).
     *
     * <p>Chạy thật 2026-09-11 khi chưa gắn {@code @Disabled}: ĐỎ đúng như dự đoán. Chủ dự án chọn
     * <b>phương án B</b> và ADR-047 được Accept 2026-09-13 ⇒ {@code @Disabled} đã gỡ, và test có
     * thêm khẳng định THỨ TỰ (D3) — thứ mà bản cũ cố ý KHÔNG hỏi tới.
     */
    @Test
    @Timeout(20)
    void pulledPartyMemberShouldBeToldTheyEnteredDungeon_overRealSockets() throws Exception {
        try (Socket socketA = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socketB = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            DataInputStream inB = new DataInputStream(socketB.getInputStream());

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            long characterA = connectCreateAndSelect(socketA, inA, "pa" + suffix);
            long characterB = connectCreateAndSelect(socketB, inB, "pb" + suffix);
            partyFacade.createParty(characterA);
            partyFacade.invite(characterA, characterB);
            long inviteId = partyFacade.listPendingInvites(characterB).get(0).inviteId();
            partyFacade.acceptInvite(inviteId, characterB);

            EnterDungeonCommand enterCommand = EnterDungeonCommand.newBuilder()
                    .setDungeonCode("sample_dungeon")
                    .build();
            sendFrame(socketA, Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, 40, enterCommand.toByteArray());
            EnterDungeonResponse enterResponseA =
                    EnterDungeonResponse.parseFrom(readUntilOpcode(inA, Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE)
                            .payload());
            assertThat(enterResponseA.getOutcome()).isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);

            // B không gửi gì. Đọc tới khi thấy boss — bằng chứng server ĐÃ dời B sang World hầm ngục
            // (world chính không có boss) — và ghi lại mọi opcode B đã nhận trên đường đi.
            // Không đặt trần số frame: @Timeout + socket timeout đã chặn trường hợp boss không bao giờ
            // tới (và giữ lớp này dưới trần CyclomaticComplexity cấp lớp của PMD).
            List<Integer> opcodesSeenByB = new ArrayList<>();
            boolean bossSeenByB = false;
            while (!bossSeenByB) {
                PacketCodec.Frame frame = readFrame(inB);
                opcodesSeenByB.add(frame.header().opcode());
                bossSeenByB = frame.header().opcode() == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE
                        && "sample_boss"
                                .equals(MonsterSpawnedEvent.parseFrom(frame.payload())
                                        .getMonsterCode());
            }
            // Chốt lại những gì B nhận TRƯỚC (và tính cả) spawn của boss — đây là thứ khẳng định
            // thứ tự ở cuối hỏi tới. Bản trước ADR-047 cố ý không hỏi, vì lúc đó chưa biết phương án
            // nào được chọn.
            List<Integer> beforeBoss = List.copyOf(opcodesSeenByB);

            // Luồng auth (gửi báo) và luồng tick hầm ngục (gửi spawn) không hứa thứ tự với nhau —
            // đọc thêm một quãng để lời báo tới SAU boss cũng được tính. Hết quãng, hoặc im lặng
            // trọn quãng (socket timeout), đều là điều kiện dừng bình thường.
            int graceMillis = 1500;
            socketB.setSoTimeout(graceMillis);
            long graceDeadline =
                    System.nanoTime() + Duration.ofMillis(graceMillis).toNanos();
            while (System.nanoTime() < graceDeadline) {
                try {
                    opcodesSeenByB.add(readFrame(inB).header().opcode());
                } catch (java.net.SocketTimeoutException e) {
                    break; // im lặng trọn quãng — không còn gì để ghi.
                }
            }

            // Dải opcode chia theo trăm (NETWORK.md §4): Dungeon = 1500-1599.
            int opcodeRangeWidth = 100;
            int dungeonRange = Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE / opcodeRangeWidth;
            assertThat(opcodesSeenByB)
                    .as("B bị kéo vào hầm ngục phải nhận ít nhất một gói dải Dungeon, đã nhận: %s", opcodesSeenByB)
                    .anyMatch(opcode -> opcode / opcodeRangeWidth == dungeonRange);
            // ADR-047 D3 — THỨ TỰ là một phần của quyết định, không phải chi tiết thi hành: client
            // dọn sạch thực thể khi nhận lời báo, nên lời báo tới SAU spawn của boss sẽ xoá mất
            // chính con boss đó, và server KHÔNG phát lại (tập "đang thấy" phía server đã có nó).
            // Khẳng định này mới là thứ gác điều đó — cái ở trên chỉ hỏi "có tới không".
            assertThat(beforeBoss)
                    .as("DungeonEnteredEvent (1504) phải tới TRƯỚC spawn của boss; B đã nhận: %s", beforeBoss)
                    .contains(Opcode.OPCODE_DUNGEON_ENTERED_EVENT_VALUE);
        }
    }

    /**
     * Đường rời hầm ngục thứ hai: một thành viên MẤT KẾT NỐI bên trong. Người ở lại không đổi World
     * nên không cần gói báo nào — nhưng họ phải thấy người kia biến mất, và instance phải còn sống
     * (World chỉ dừng khi RỖNG, {@code DungeonInstanceRegistry.leaveInstance}) để họ tự thoát được.
     */
    @Test
    @Timeout(20)
    void remainingMemberShouldSeeDespawnAndStillExit_whenPartyMemberDisconnectsInsideDungeon_overRealSockets()
            throws Exception {
        try (Socket socketA = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socketA.setSoTimeout(5000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            long characterA = connectCreateAndSelect(socketA, inA, "ra" + suffix);
            long characterB;
            try (Socket socketB = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                socketB.setSoTimeout(5000);
                DataInputStream inB = new DataInputStream(socketB.getInputStream());
                characterB = connectCreateAndSelect(socketB, inB, "rb" + suffix);
                partyFacade.createParty(characterA);
                partyFacade.invite(characterA, characterB);
                long inviteId =
                        partyFacade.listPendingInvites(characterB).get(0).inviteId();
                partyFacade.acceptInvite(inviteId, characterB);

                EnterDungeonCommand enterCommand = EnterDungeonCommand.newBuilder()
                        .setDungeonCode("sample_dungeon")
                        .build();
                sendFrame(socketA, Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, 50, enterCommand.toByteArray());
                assertThat(EnterDungeonResponse.parseFrom(
                                        readUntilOpcode(inA, Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE)
                                                .payload())
                                .getOutcome())
                        .isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);

                // B đi sang phải TRONG hầm ngục, A phải thấy — bằng chứng A đang nhìn thấy B ở CHÍNH
                // World hầm ngục (cùng cách partyShouldEnterAndExitDungeonAsSeparateWorld_...). Mỗi
                // test dựng server riêng và chỉ B di chuyển, nên CharacterMovedEvent đầu tiên A nhận
                // chính là của B — khẳng định id để sai thì đỏ to chứ không lặng lẽ đọc nhầm.
                MoveCommand moveCommand =
                        MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
                sendFrame(socketB, Opcode.OPCODE_MOVE_COMMAND_VALUE, 51, moveCommand.toByteArray());
                CharacterMovedEvent movedSeenByA =
                        CharacterMovedEvent.parseFrom(readUntilOpcode(inA, Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE)
                                .payload());
                assertThat(movedSeenByA.getCharacterId()).isEqualTo(characterB);
                assertThat(movedSeenByA.getX()).isGreaterThan(0f);
            } // B đóng socket ⇒ channelInactive ⇒ handleDisconnectIfInDungeon.

            // A đã rời world chính trước B (cùng hộp thư, A xếp trước) nên world chính không báo A
            // despawn của B; despawn duy nhất A nhận là từ World hầm ngục.
            CharacterDespawnedEvent despawnSeenByA = CharacterDespawnedEvent.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE)
                            .payload());
            assertThat(despawnSeenByA.getCharacterId()).isEqualTo(characterB);

            sendFrame(
                    socketA,
                    Opcode.OPCODE_EXIT_DUNGEON_COMMAND_VALUE,
                    52,
                    ExitDungeonCommand.newBuilder().build().toByteArray());
            assertThat(ExitDungeonResponse.parseFrom(readUntilOpcode(inA, Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE)
                                    .payload())
                            .getOutcome())
                    .isEqualTo(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK);
        }
    }

    /**
     * Đường rời hầm ngục thứ ba: RỜI NHÓM khi đang ở trong. Rời nhóm không dời ai khỏi instance (không
     * đổi World ⇒ không cần gói báo nào), nên điều duy nhất phải bảo đảm là người đó KHÔNG bị kẹt:
     * {@code exitDungeon} tra theo nhân vật, không theo nhóm.
     */
    @Test
    @Timeout(15)
    void memberShouldStillExitDungeon_afterLeavingPartyInside_overRealSockets() throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            long characterId = connectCreateAndSelect(
                    socket, in, "lp" + UUID.randomUUID().toString().substring(0, 6));
            partyFacade.createParty(characterId); // nhóm 1 người — đủ điều kiện vào hầm ngục.

            EnterDungeonCommand enterCommand = EnterDungeonCommand.newBuilder()
                    .setDungeonCode("sample_dungeon")
                    .build();
            sendFrame(socket, Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, 60, enterCommand.toByteArray());
            assertThat(EnterDungeonResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE)
                                    .payload())
                            .getOutcome())
                    .isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);

            assertThat(partyFacade.leaveParty(characterId)).isEqualTo(dev.kage.party.api.PartyFacade.LeaveResult.OK);

            sendFrame(
                    socket,
                    Opcode.OPCODE_EXIT_DUNGEON_COMMAND_VALUE,
                    61,
                    ExitDungeonCommand.newBuilder().build().toByteArray());
            assertThat(ExitDungeonResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE)
                                    .payload())
                            .getOutcome())
                    .isEqualTo(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK);
        }
    }

    @Test
    @Timeout(15)
    void shouldDeclineDuelChallenge_overRealSockets() throws Exception {
        // Phase 8 lát cắt 4 — challenger phải nhận DuelChallengeDeclinedEvent khi bị từ chối,
        // KHÔNG có DuelStartedEvent nào phát ra (không World riêng nào được tạo).
        try (Socket socketA = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socketB = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            DataInputStream inB = new DataInputStream(socketB.getInputStream());

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            connectCreateAndSelectNamed(socketA, inA, "duA" + suffix);
            CreatedCharacter characterB = connectCreateAndSelectNamed(socketB, inB, "duB" + suffix);

            ChallengeDuelCommand challenge = ChallengeDuelCommand.newBuilder()
                    .setTargetName(characterB.name())
                    .build();
            sendFrame(socketA, Opcode.OPCODE_CHALLENGE_DUEL_COMMAND_VALUE, 40, challenge.toByteArray());
            ChallengeDuelResponse challengeResponse =
                    ChallengeDuelResponse.parseFrom(readUntilOpcode(inA, Opcode.OPCODE_CHALLENGE_DUEL_RESPONSE_VALUE)
                            .payload());
            assertThat(challengeResponse.getOutcome()).isEqualTo(ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_OK);
            long challengeId = challengeResponse.getChallengeId();

            DuelChallengeReceivedEvent received = DuelChallengeReceivedEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_DUEL_CHALLENGE_RECEIVED_EVENT_VALUE)
                            .payload());
            assertThat(received.getChallengeId()).isEqualTo(challengeId);

            DeclineDuelCommand decline =
                    DeclineDuelCommand.newBuilder().setChallengeId(challengeId).build();
            sendFrame(socketB, Opcode.OPCODE_DECLINE_DUEL_COMMAND_VALUE, 41, decline.toByteArray());
            DeclineDuelResponse declineResponse =
                    DeclineDuelResponse.parseFrom(readUntilOpcode(inB, Opcode.OPCODE_DECLINE_DUEL_RESPONSE_VALUE)
                            .payload());
            assertThat(declineResponse.getOutcome()).isEqualTo(DeclineDuelOutcome.DECLINE_DUEL_OUTCOME_OK);

            DuelChallengeDeclinedEvent declinedEvent = DuelChallengeDeclinedEvent.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_DUEL_CHALLENGE_DECLINED_EVENT_VALUE)
                            .payload());
            assertThat(declinedEvent.getChallengeId()).isEqualTo(challengeId);

            // Thách đấu cũ đã bị dọn — chấp nhận lại (challengeId cũ) phải NOT_FOUND.
            AcceptDuelCommand lateAccept =
                    AcceptDuelCommand.newBuilder().setChallengeId(challengeId).build();
            sendFrame(socketB, Opcode.OPCODE_ACCEPT_DUEL_COMMAND_VALUE, 42, lateAccept.toByteArray());
            AcceptDuelResponse lateAcceptResponse =
                    AcceptDuelResponse.parseFrom(readUntilOpcode(inB, Opcode.OPCODE_ACCEPT_DUEL_RESPONSE_VALUE)
                            .payload());
            assertThat(lateAcceptResponse.getOutcome()).isEqualTo(AcceptDuelOutcome.ACCEPT_DUEL_OUTCOME_NOT_FOUND);
        }
    }

    @Test
    @Timeout(30)
    void shouldFightDuelToDeathAndUpdateRating_overRealSockets() throws Exception {
        // Phase 8 lát cắt 4 — chấp nhận thách đấu đưa CẢ 2 vào 1 World riêng (ADR-026, giống
        // dungeon), đánh tới khi 1 người chết → DuelResultEvent cho CẢ 2 + rating cập nhật qua
        // GetPvpRatingRequest (Elo, K=32: thắng +16, thua -16 khi cùng rating khởi điểm 1000).
        try (Socket socketA = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socketB = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socketC = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socketA.setSoTimeout(10000);
            socketB.setSoTimeout(10000);
            socketC.setSoTimeout(10000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            DataInputStream inB = new DataInputStream(socketB.getInputStream());
            DataInputStream inC = new DataInputStream(socketC.getInputStream());

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            CreatedCharacter createdA = connectCreateAndSelectNamed(socketA, inA, "fdA" + suffix);
            CreatedCharacter createdB = connectCreateAndSelectNamed(socketB, inB, "fdB" + suffix);
            long characterA = createdA.characterId();
            long characterB = createdB.characterId();
            // C KHÔNG tham gia trận — ở lại world chính làm chứng nhân (verify A/B đã RỜI world
            // chính thật, cùng cách partyShouldEnterAndExitDungeonAsSeparateWorld_overRealSockets).
            connectCreateAndSelect(socketC, inC, "fdC" + suffix);

            // AoI sync() 20Hz — đợi C thật sự THẤY A/B spawn trước khi để họ vào duel, cùng lý do
            // partyShouldEnterAndExitDungeonAsSeparateWorld_overRealSockets (xem comment ở đó).
            java.util.HashSet<Long> spawnedSeenByC = new java.util.HashSet<Long>();
            spawnedSeenByC.add(
                    CharacterSpawnedEvent.parseFrom(readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                                    .payload())
                            .getCharacterId());
            spawnedSeenByC.add(
                    CharacterSpawnedEvent.parseFrom(readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                                    .payload())
                            .getCharacterId());
            assertThat(spawnedSeenByC).containsExactlyInAnyOrder(characterA, characterB);

            ChallengeDuelCommand challenge = ChallengeDuelCommand.newBuilder()
                    .setTargetName(createdB.name())
                    .build();
            sendFrame(socketA, Opcode.OPCODE_CHALLENGE_DUEL_COMMAND_VALUE, 40, challenge.toByteArray());
            ChallengeDuelResponse challengeResponse =
                    ChallengeDuelResponse.parseFrom(readUntilOpcode(inA, Opcode.OPCODE_CHALLENGE_DUEL_RESPONSE_VALUE)
                            .payload());
            assertThat(challengeResponse.getOutcome()).isEqualTo(ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_OK);
            long challengeId = challengeResponse.getChallengeId();
            DuelChallengeReceivedEvent received = DuelChallengeReceivedEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_DUEL_CHALLENGE_RECEIVED_EVENT_VALUE)
                            .payload());
            assertThat(received.getChallengerCharacterId()).isEqualTo(characterA);

            AcceptDuelCommand accept =
                    AcceptDuelCommand.newBuilder().setChallengeId(challengeId).build();
            sendFrame(socketB, Opcode.OPCODE_ACCEPT_DUEL_COMMAND_VALUE, 41, accept.toByteArray());
            AcceptDuelResponse acceptResponse =
                    AcceptDuelResponse.parseFrom(readUntilOpcode(inB, Opcode.OPCODE_ACCEPT_DUEL_RESPONSE_VALUE)
                            .payload());
            assertThat(acceptResponse.getOutcome()).isEqualTo(AcceptDuelOutcome.ACCEPT_DUEL_OUTCOME_OK);

            DuelStartedEvent startedForB = DuelStartedEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE).payload());
            assertThat(startedForB.getOpponentCharacterId()).isEqualTo(characterA);
            DuelStartedEvent startedForA = DuelStartedEvent.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE).payload());
            assertThat(startedForA.getOpponentCharacterId()).isEqualTo(characterB);

            // C (world chính) phải thấy CẢ A lẫn B despawn — chứng minh cách ly instance thật.
            java.util.HashSet<Long> despawnedIds = new java.util.HashSet<Long>();
            despawnedIds.add(CharacterDespawnedEvent.parseFrom(
                            readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE)
                                    .payload())
                    .getCharacterId());
            despawnedIds.add(CharacterDespawnedEvent.parseFrom(
                            readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE)
                                    .payload())
                    .getCharacterId());
            assertThat(despawnedIds).containsExactlyInAnyOrder(characterA, characterB);

            // Cùng chỉ số nền — cả 2 tự đánh nhau tới khi 1 người chết (CombatConstants.CHARACTER_MAX_HP=100).
            SetTargetCommand targetB =
                    SetTargetCommand.newBuilder().setTargetEntityId(characterB).build();
            sendFrame(socketA, Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, 42, targetB.toByteArray());
            SetTargetCommand targetA =
                    SetTargetCommand.newBuilder().setTargetEntityId(characterA).build();
            sendFrame(socketB, Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, 42, targetA.toByteArray());
            // Cả 2 phía bấm đánh — nhân vật không tự đánh nữa (2026-08-09), kể cả trong duel.
            Thread spammerA = startAttackSpammer(socketA);
            Thread spammerB = startAttackSpammer(socketB);

            DuelResultEvent resultForA = DuelResultEvent.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_DUEL_RESULT_EVENT_VALUE).payload());
            DuelResultEvent resultForB = DuelResultEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_DUEL_RESULT_EVENT_VALUE).payload());
            spammerA.interrupt(); // trận xong — ngừng bấm trước khi test gửi tiếp GetPvpRatingRequest
            spammerB.interrupt();
            assertThat(resultForA).isEqualTo(resultForB);
            assertThat(java.util.Set.of(resultForA.getWinnerCharacterId(), resultForA.getLoserCharacterId()))
                    .containsExactlyInAnyOrder(characterA, characterB);
            // Cùng rating khởi điểm 1000 — thắng +16, thua -16 (EloRating K=32, xem feature-pvp).
            assertThat(resultForA.getWinnerNewRating()).isEqualTo(1016);
            assertThat(resultForA.getLoserNewRating()).isEqualTo(984);

            // C phải thấy CẢ A lẫn B spawn lại — chứng minh cả 2 đã quay về world chính thật.
            java.util.HashSet<Long> respawnedIds = new java.util.HashSet<Long>();
            respawnedIds.add(
                    CharacterSpawnedEvent.parseFrom(readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                                    .payload())
                            .getCharacterId());
            respawnedIds.add(
                    CharacterSpawnedEvent.parseFrom(readUntilOpcode(inC, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                                    .payload())
                            .getCharacterId());
            assertThat(respawnedIds).containsExactlyInAnyOrder(characterA, characterB);

            long winnerCharacterId = resultForA.getWinnerCharacterId();
            Socket winnerSocket = winnerCharacterId == characterA ? socketA : socketB;
            DataInputStream winnerIn = winnerCharacterId == characterA ? inA : inB;
            GetPvpRatingRequest ratingRequest = GetPvpRatingRequest.newBuilder().build();
            sendFrame(winnerSocket, Opcode.OPCODE_GET_PVP_RATING_REQUEST_VALUE, 43, ratingRequest.toByteArray());
            GetPvpRatingResponse ratingResponse = GetPvpRatingResponse.parseFrom(
                    readUntilOpcode(winnerIn, Opcode.OPCODE_GET_PVP_RATING_RESPONSE_VALUE)
                            .payload());
            assertThat(ratingResponse.getRating()).isEqualTo(1016);
            assertThat(ratingResponse.getWins()).isEqualTo(1);
            assertThat(ratingResponse.getLosses()).isZero();
        }
    }

    @Test
    @Timeout(15)
    void shouldForfeitDuel_overRealSockets() throws Exception {
        // Phase 8 lát cắt 4 — bỏ cuộc giữa chừng tính THUA thật (khác ExitDungeonCommand), đối
        // phương thắng NGAY không cần hạ gục.
        try (Socket socketA = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socketB = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            DataInputStream inB = new DataInputStream(socketB.getInputStream());

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            long characterA = connectCreateAndSelect(socketA, inA, "foA" + suffix);
            CreatedCharacter createdB = connectCreateAndSelectNamed(socketB, inB, "foB" + suffix);

            ChallengeDuelCommand challenge = ChallengeDuelCommand.newBuilder()
                    .setTargetName(createdB.name())
                    .build();
            sendFrame(socketA, Opcode.OPCODE_CHALLENGE_DUEL_COMMAND_VALUE, 40, challenge.toByteArray());
            long challengeId = ChallengeDuelResponse.parseFrom(
                            readUntilOpcode(inA, Opcode.OPCODE_CHALLENGE_DUEL_RESPONSE_VALUE)
                                    .payload())
                    .getChallengeId();
            readUntilOpcode(inB, Opcode.OPCODE_DUEL_CHALLENGE_RECEIVED_EVENT_VALUE);

            AcceptDuelCommand accept =
                    AcceptDuelCommand.newBuilder().setChallengeId(challengeId).build();
            sendFrame(socketB, Opcode.OPCODE_ACCEPT_DUEL_COMMAND_VALUE, 41, accept.toByteArray());
            readUntilOpcode(inB, Opcode.OPCODE_ACCEPT_DUEL_RESPONSE_VALUE);
            readUntilOpcode(inA, Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE);
            readUntilOpcode(inB, Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE);

            // A bỏ cuộc — B thắng NGAY dù chưa đánh đòn nào. ArenaInstanceRegistry.forfeitDuel chốt
            // rating + gửi DuelResultEvent NGAY TRONG lúc xử lý lệnh (đồng bộ trên authExecutor) —
            // DuelResultEvent tới A TRƯỚC ExitDuelResponse (khác Trade: Response luôn tới actor
            // trước Event, ở đây CẢ 2 đều gửi cho A nên đọc đúng thứ tự thật, không phải quy ước
            // ngược lại).
            ExitDuelCommand exitCommand = ExitDuelCommand.newBuilder().build();
            sendFrame(socketA, Opcode.OPCODE_EXIT_DUEL_COMMAND_VALUE, 42, exitCommand.toByteArray());

            DuelResultEvent resultForA = DuelResultEvent.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_DUEL_RESULT_EVENT_VALUE).payload());
            assertThat(resultForA.getLoserCharacterId()).isEqualTo(characterA);

            ExitDuelResponse exitResponse = ExitDuelResponse.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_EXIT_DUEL_RESPONSE_VALUE).payload());
            assertThat(exitResponse.getOutcome()).isEqualTo(ExitDuelOutcome.EXIT_DUEL_OUTCOME_OK);

            DuelResultEvent resultForB = DuelResultEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_DUEL_RESULT_EVENT_VALUE).payload());
            assertThat(resultForB.getWinnerCharacterId()).isNotEqualTo(characterA);

            // A bỏ cuộc lần 2 (không còn ở duel nào) — NOT_IN_DUEL.
            sendFrame(socketA, Opcode.OPCODE_EXIT_DUEL_COMMAND_VALUE, 43, exitCommand.toByteArray());
            ExitDuelResponse secondExit = ExitDuelResponse.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_EXIT_DUEL_RESPONSE_VALUE).payload());
            assertThat(secondExit.getOutcome()).isEqualTo(ExitDuelOutcome.EXIT_DUEL_OUTCOME_NOT_IN_DUEL);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldDamageMonster_overRealSockets() throws Exception {
        // Verify Phase 4 lát cắt 1 qua chính giao thức dây: di chuyển tới gần quái bù nhìn (spawn
        // cố định (5,5), WorldFacade) → SetTargetCommand → nhận AttackResultEvent thật.
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            long characterId = connectCreateAndSelect(
                    socket, in, "atk" + UUID.randomUUID().toString().substring(0, 6));

            // Nhân vật mới spawn (0,0) — quái ở (5,5), cách ~7.07m, đã trong AoI (15m) nên
            // MonsterSpawnedEvent tới ngay, nhưng CHƯA trong tầm đánh (2m) — phải di chuyển lại gần.
            MonsterSpawnedEvent monsterSpawned =
                    MonsterSpawnedEvent.parseFrom(readUntilOpcode(in, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE)
                            .payload());
            long monsterId = monsterSpawned.getMonsterId();

            // Hướng chéo (0.7071, 0.7071) — chuẩn hoá, giống hệt client thật gửi (server tự nhân
            // tốc độ, movement.proto). ~1.2s ở 5 m/s đưa nhân vật vào trong tầm đánh (còn cách quái ~1m).
            MoveCommand moveCommand =
                    MoveCommand.newBuilder().setDirX(0.7071f).setDirY(0.7071f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());
            Thread.sleep(1200);
            MoveCommand stopCommand =
                    MoveCommand.newBuilder().setDirX(0f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 11, stopCommand.toByteArray());

            SetTargetCommand setTarget =
                    SetTargetCommand.newBuilder().setTargetEntityId(monsterId).build();
            sendFrame(socket, Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, 12, setTarget.toByteArray());
            startAttackSpammer(socket); // đánh thường phải bấm tay từ 2026-08-09

            // PHÁT HIỆN THẬT (Phase 8 lát cắt 2): quái giờ đánh trả CŨNG tới được client (trước đây
            // bị rơi mất với nhân vật chơi solo — xem WorldFacade.handleCombatResults) — 2 chiều
            // đòn (nhân vật→quái, quái→nhân vật) có thể tới theo BẤT KỲ thứ tự nào, lọc đúng đòn
            // của NHÂN VẬT thay vì giả định đòn ĐẦU TIÊN luôn là của nó.
            AttackResultEvent attackResult;
            do {
                attackResult = AttackResultEvent.parseFrom(readUntilOpcode(in, Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE)
                        .payload());
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
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "equip" + UUID.randomUUID().toString().substring(0, 6));

            EquipItemCommand equipCommand = EquipItemCommand.newBuilder()
                    .setItemId(1L)
                    .setSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON)
                    .build();
            sendFrame(socket, Opcode.OPCODE_EQUIP_ITEM_COMMAND_VALUE, 10, equipCommand.toByteArray());

            EquipItemResponse response = EquipItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_EQUIP_ITEM_RESPONSE_VALUE).payload());
            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getErrorKey()).isEqualTo("error.item_not_found");
            // ADR-043 D4 — thao tác THẤT BẠI thì KHÔNG gửi máu: 0 nghĩa là "giữ nguyên số của bạn",
            // client không được lấy 0 làm mẫu số vạch máu.
            assertThat(response.getMaxHp()).isZero();
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveUnequipItemResponse_overRealSockets() throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "unequip" + UUID.randomUUID().toString().substring(0, 6));

            UnequipItemCommand unequipCommand = UnequipItemCommand.newBuilder()
                    .setSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON)
                    .build();
            sendFrame(socket, Opcode.OPCODE_UNEQUIP_ITEM_COMMAND_VALUE, 10, unequipCommand.toByteArray());

            UnequipItemResponse response =
                    UnequipItemResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_UNEQUIP_ITEM_RESPONSE_VALUE)
                            .payload());
            assertThat(response.getSuccess()).isFalse(); // slot rỗng — không có gì để gỡ
            assertThat(response.getErrorKey()).isEqualTo("error.slot_not_equipped");
            assertThat(response.getMaxHp()).isZero(); // ADR-043 D4 — thất bại thì không gửi máu
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
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "enhance" + UUID.randomUUID().toString().substring(0, 6));

            EnhanceItemCommand enhanceCommand = EnhanceItemCommand.newBuilder()
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .setItemId(1L)
                    .build();
            sendFrame(socket, Opcode.OPCODE_ENHANCE_ITEM_COMMAND_VALUE, 10, enhanceCommand.toByteArray());

            EnhanceItemResponse response =
                    EnhanceItemResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_ENHANCE_ITEM_RESPONSE_VALUE)
                            .payload());
            assertThat(response.getOutcome()).isEqualTo(EnhanceOutcome.ENHANCE_OUTCOME_ITEM_NOT_FOUND);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveUseItemResponse_overRealSockets() throws Exception {
        // ADR-046 D5. Cùng lý do với clientShouldReceiveEnhanceItemResponse_overRealSockets:
        // fakeInventoryFacade() luôn trả túi đồ RỖNG nên id=1 chắc chắn không tồn tại — test đúng
        // nhánh ITEM_NOT_FOUND, verify dây thật (opcode 511 dispatch → decode → Virtual Thread →
        // encode 512). Nhánh uống thành công đã phủ ở UseItemUseCaseTest + HealingPillIntegrationTest.
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "useitem" + UUID.randomUUID().toString().substring(0, 5));

            UseItemCommand useCommand =
                    UseItemCommand.newBuilder().setInventoryItemId(1L).build();
            sendFrame(socket, Opcode.OPCODE_USE_ITEM_COMMAND_VALUE, 11, useCommand.toByteArray());

            UseItemResponse response = UseItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_USE_ITEM_RESPONSE_VALUE).payload());
            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getErrorKey()).isEqualTo("error.item_not_found");
            assertThat(response.getRemainingQuantity()).isZero();
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveEmptyShopListing_overRealSockets() throws Exception {
        // fakeShopFacade() luôn trả catalog RỖNG (Fake, không DB thật) — verify dây thật (opcode
        // dispatch → decode → Virtual Thread → encode response), không phải nội dung catalog thật
        // (đã phủ ở ShopIntegrationTest tầng dưới).
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "shoplist" + UUID.randomUUID().toString().substring(0, 6));

            ListShopItemsRequest request = ListShopItemsRequest.newBuilder().build();
            sendFrame(socket, Opcode.OPCODE_LIST_SHOP_ITEMS_REQUEST_VALUE, 10, request.toByteArray());

            ListShopItemsResponse response =
                    ListShopItemsResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE)
                            .payload());
            assertThat(response.getListingsList()).isEmpty();
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveBuyItemResponse_overRealSockets() throws Exception {
        // Cùng lý do clientShouldReceiveEnhanceItemResponse_overRealSockets: catalog rỗng nên
        // itemTemplateId=1 chắc chắn không có trong shop — verify đúng nhánh ITEM_NOT_IN_SHOP.
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "buy" + UUID.randomUUID().toString().substring(0, 6));

            BuyItemCommand command = BuyItemCommand.newBuilder()
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .setItemTemplateId(1L)
                    .setQuantity(1)
                    .build();
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
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "sell" + UUID.randomUUID().toString().substring(0, 6));

            SellItemCommand command = SellItemCommand.newBuilder()
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .setItemId(1L)
                    .setQuantity(1)
                    .build();
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
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "npc" + UUID.randomUUID().toString().substring(0, 6));

            TalkToNpcCommand command =
                    TalkToNpcCommand.newBuilder().setNpcCode("town_elder").build();
            sendFrame(socket, Opcode.OPCODE_TALK_TO_NPC_COMMAND_VALUE, 10, command.toByteArray());

            ErrorResponse response = ErrorResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_ERROR_VALUE).payload());
            assertThat(response.getErrorKey()).isEqualTo("error.npc_not_found");
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveClaimQuestRewardResponse_overRealSockets() throws Exception {
        // fakeQuestFacade()'s QuestTemplatePort luôn trả rỗng — verify đúng nhánh QUEST_NOT_FOUND
        // (kiểm tra template TRƯỚC progress, xem ClaimQuestRewardUseCase.execute).
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "quest" + UUID.randomUUID().toString().substring(0, 6));

            ClaimQuestRewardCommand command = ClaimQuestRewardCommand.newBuilder()
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .setQuestCode("kill_training_dummies")
                    .build();
            sendFrame(socket, Opcode.OPCODE_CLAIM_QUEST_REWARD_COMMAND_VALUE, 10, command.toByteArray());

            ClaimQuestRewardResponse response = ClaimQuestRewardResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_CLAIM_QUEST_REWARD_RESPONSE_VALUE)
                            .payload());
            assertThat(response.getOutcome())
                    .isEqualTo(ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_QUEST_NOT_FOUND);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveSendFriendRequestResponse_overRealSockets() throws Exception {
        // Nhánh 1: tên không tồn tại ⇒ TARGET_NOT_FOUND (dây thật: opcode dispatch → decode →
        // Virtual Thread → CharacterFacade.findByName → encode response).
        // Nhánh 2: tên CÓ tồn tại nhưng fakeFriendFacade()'s FriendPort.sendRequest luôn ném — đúng
        // hình dạng một lỗi DB thật. Handler chạy trên Virtual Thread của authExecutor nên exception
        // KHÔNG tới exceptionCaught của pipeline: trước bản vá 2026-09-24 client không nhận gì và
        // treo vô thời hạn (ở đây: hết @Timeout). Gộp chung một hàm thay vì tách: lớp này đã chạm
        // trần CyclomaticComplexity cả lớp của PMD (mỗi hàm mới cộng ít nhất 1).
        try (Socket target = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            target.setSoTimeout(5000);
            socket.setSoTimeout(5000);
            String targetName = connectCreateAndSelectNamed(
                            target,
                            new DataInputStream(target.getInputStream()),
                            "frtgt" + UUID.randomUUID().toString().substring(0, 6))
                    .name();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "friend" + UUID.randomUUID().toString().substring(0, 6));

            SendFriendRequestCommand command = SendFriendRequestCommand.newBuilder()
                    .setTargetName("khong_ton_tai")
                    .build();
            sendFrame(socket, Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE, 10, command.toByteArray());

            SendFriendRequestResponse response = SendFriendRequestResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE)
                            .payload());
            assertThat(response.getOutcome())
                    .isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_TARGET_NOT_FOUND);

            SendFriendRequestCommand failingCommand = SendFriendRequestCommand.newBuilder()
                    .setTargetName(targetName)
                    .build();
            sendFrame(socket, Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE, 11, failingCommand.toByteArray());

            ErrorResponse error = ErrorResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_ERROR_VALUE).payload());
            assertThat(error.getErrorKey()).isEqualTo("error.send_friend_request_failed");
        }
    }

    @Test
    @Timeout(15)
    void clientShouldReceiveSendMailResponse_overRealSockets() throws Exception {
        // Nhánh 1 — cùng lý do clientShouldReceiveSendFriendRequestResponse_overRealSockets: tên
        // người nhận bất kỳ chắc chắn không tồn tại ⇒ RECIPIENT_NOT_FOUND, KHÔNG chạm
        // fakeMailFacade()'s MailRepositoryPort.
        // Nhánh 2 — người nhận CÓ thật nên đi tới MailRepositoryPort.send, mà fake luôn ném: đúng
        // hình dạng một lỗi DB thật. handleSendMailBlocking không tự bắt gì; trước lưới
        // executeBlocking (2026-09-24) client không nhận gì và treo tới hết @Timeout. Gộp chung một
        // hàm: lớp này đã chạm trần CyclomaticComplexity cả lớp của PMD.
        try (Socket recipient = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            recipient.setSoTimeout(5000);
            socket.setSoTimeout(5000);
            String recipientName = connectCreateAndSelectNamed(
                            recipient,
                            new DataInputStream(recipient.getInputStream()),
                            "mlrcv" + UUID.randomUUID().toString().substring(0, 6))
                    .name();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "mail" + UUID.randomUUID().toString().substring(0, 6));

            SendMailCommand command = SendMailCommand.newBuilder()
                    .setRecipientName("khong_ton_tai")
                    .setSubject("s")
                    .setBody("b")
                    .build();
            sendFrame(socket, Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE, 10, command.toByteArray());

            SendMailResponse response = SendMailResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_SEND_MAIL_RESPONSE_VALUE).payload());
            assertThat(response.getOutcome()).isEqualTo(SendMailOutcome.SEND_MAIL_OUTCOME_RECIPIENT_NOT_FOUND);

            SendMailCommand failingCommand = SendMailCommand.newBuilder()
                    .setRecipientName(recipientName)
                    .setSubject("s")
                    .setBody("b")
                    .build();
            sendFrame(socket, Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE, 11, failingCommand.toByteArray());

            ErrorResponse error = ErrorResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_ERROR_VALUE).payload());
            assertThat(error.getErrorKey()).isEqualTo("error.server_internal");
            assertThat(error.getRelatedSequenceId()).isEqualTo(11);
        }
    }

    @Test
    @Timeout(15)
    void twoClientsShouldSeeWorldChatMessage_overRealSockets() throws Exception {
        // World chat = broadcast toàn server, KHÔNG cần AoI/cùng vị trí — verify dây thật (opcode
        // dispatch → decode → Virtual Thread → ChatFacade.sendMessage → ConnectedCharacterRegistry
        // .broadcastToAll → encode) cho CẢ người gửi (tự thấy tin của mình) lẫn người khác.
        try (Socket socketA = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket socketB = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
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
                    readUntilOpcode(inA, Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE)
                            .payload());
            assertThat(ackResponse.getOutcome()).isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK);

            ChatMessageEvent eventSeenByA = ChatMessageEvent.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE).payload());
            assertThat(eventSeenByA.getBody()).isEqualTo("xin chào thế giới");
            ChatMessageEvent eventSeenByB = ChatMessageEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE).payload());
            assertThat(eventSeenByB.getBody()).isEqualTo("xin chào thế giới");
        }
    }

    /** Tên nhân vật THẬT (sinh ngẫu nhiên "Hero...") — KHÁC {@code username} tài khoản. */
    private record CreatedCharacter(long characterId, String name) {}

    /**
     * Như {@link #connectCreateAndSelect} nhưng trả thêm tên nhân vật THẬT — cần cho
     * {@code ChallengeDuelCommand.target_name} (server tra theo tên nhân vật, không phải username
     * tài khoản, xem javadoc {@code CreatedCharacter}).
     */
    private static CreatedCharacter connectCreateAndSelectNamed(Socket socket, DataInputStream in, String username)
            throws Exception {
        ConnectRequest connectRequest =
                ConnectRequest.newBuilder().setUsername(username).build();
        sendFrame(socket, Opcode.OPCODE_CONNECT_REQUEST_VALUE, 1, connectRequest.toByteArray());
        assertThat(ConnectResponse.parseFrom(readFrame(in).payload()).getSuccess())
                .isTrue();

        CreateCharacterRequest createRequest = CreateCharacterRequest.newBuilder()
                .setName("Hero" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        sendFrame(socket, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 2, createRequest.toByteArray());
        CreateCharacterResponse createResponse =
                CreateCharacterResponse.parseFrom(readFrame(in).payload());
        long characterId = createResponse.getCharacter().getId();
        String name = createResponse.getCharacter().getName();

        SelectCharacterRequest selectRequest =
                SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        sendFrame(socket, Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, 3, selectRequest.toByteArray());
        readFrame(in); // SelectCharacterResponse — nội dung đã verify ở GatewayPipelineTest

        return new CreatedCharacter(characterId, name);
    }

    private static long connectCreateAndSelect(Socket socket, DataInputStream in, String username) throws Exception {
        ConnectRequest connectRequest =
                ConnectRequest.newBuilder().setUsername(username).build();
        sendFrame(socket, Opcode.OPCODE_CONNECT_REQUEST_VALUE, 1, connectRequest.toByteArray());
        assertThat(ConnectResponse.parseFrom(readFrame(in).payload()).getSuccess())
                .isTrue();

        // Character.validateName: chỉ chữ/số, KHÔNG dấu gạch ngang (feature-character/CLAUDE.md).
        CreateCharacterRequest createRequest = CreateCharacterRequest.newBuilder()
                .setName("Hero" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        sendFrame(socket, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 2, createRequest.toByteArray());
        CreateCharacterResponse createResponse =
                CreateCharacterResponse.parseFrom(readFrame(in).payload());
        long characterId = createResponse.getCharacter().getId();

        SelectCharacterRequest selectRequest =
                SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        sendFrame(socket, Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, 3, selectRequest.toByteArray());
        readFrame(in); // SelectCharacterResponse — nội dung đã verify ở GatewayPipelineTest

        return characterId;
    }

    // synchronized trên chính socket: từ 2026-08-09 có luồng nền bấm đánh (startAttackSpammer) ghi
    // song song với luồng test chính trên CÙNG socket — 2 lời gọi write() đan nhau sẽ cắt đôi một
    // frame và hỏng cả dòng dữ liệu. Khoá ở đây thay vì bắt mọi chỗ gọi phải tự nhớ.
    private static void sendFrame(Socket socket, int opcode, int sequenceId, byte[] payload) throws Exception {
        synchronized (socket) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
            byte[] frame = PacketCodec.encodeFrame(header, payload);
            out.writeInt(frame.length);
            out.write(frame);
            out.flush();
        }
    }

    /**
     * Bấm ĐÁNH nền — gửi {@code AttackCommand} đều đặn trên socket này tới khi bị {@code interrupt}
     * hoặc socket đóng.
     *
     * <p>Cần từ 2026-08-09: đánh thường không còn tự động (chủ dự án quyết sau playtest, xem
     * {@code CombatSystem}), mà các test này chờ ĐỌC frame nên không thể vừa đọc vừa bấm trên cùng
     * một luồng. Nhịp 200ms dày hơn cooldown thật (1 đòn/giây) — lệnh thừa là no-op ở server.
     */
    // catch (Exception) là CỐ Ý: sendFrame khai báo `throws Exception`, và mọi lỗi ở luồng nền này
    // đều có đúng một nghĩa — socket đã đóng vì test kết thúc. Không có gì để xử lý riêng, cũng
    // không được để nó nổi lên làm hỏng test khác (cùng lý do WorldFacade.tickTask bọc Throwable).
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static Thread startAttackSpammer(Socket socket) {
        byte[] payload = AttackCommand.getDefaultInstance().toByteArray();
        Thread spammer = new Thread(
                () -> {
                    for (int sequenceId = 9000; !Thread.currentThread().isInterrupted(); sequenceId++) {
                        try {
                            sendFrame(socket, Opcode.OPCODE_ATTACK_COMMAND_VALUE, sequenceId, payload);
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return; // test đã xong — kết thúc bình thường, không phải lỗi
                        } catch (Exception e) {
                            return; // socket đóng lúc test kết thúc — cùng lý do trên
                        }
                    }
                },
                "attack-spammer");
        spammer.setDaemon(true);
        spammer.start();
        return spammer;
    }

    /** AoI Event tới bất kỳ lúc nào giữa các Response khác — đọc tới khi thấy đúng opcode cần. */
    private static PacketCodec.Frame readUntilOpcode(DataInputStream in, int expectedOpcode) throws Exception {
        java.util.Map<Integer, Integer> seen = new java.util.HashMap<>();
        for (int attempt = 0; attempt < 1000; attempt++) {
            PacketCodec.Frame frame = readFrame(in);
            if (frame.header().opcode() == expectedOpcode) {
                return frame;
            }
            seen.merge(frame.header().opcode(), 1, Integer::sum);
        }
        throw new AssertionError("Không thấy opcode " + expectedOpcode + " sau 1000 frame, đã thấy: " + seen);
    }

    private static void sendPing(Socket socket, int sequenceId, long clientTimestampMs) throws Exception {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        PingCommand ping =
                PingCommand.newBuilder().setClientTimestampMs(clientTimestampMs).build();
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

    // ============================================================================================
    // ADR-027 — TLS thật qua socket (KHÔNG dùng field server/mainWorldChannels/characterRegistry
    // chính, tự start/stop RIÊNG qua try-with-resources — tránh @AfterEach dọn nhầm server TLS
    // thay vì server chính do startServer() tạo).
    // ============================================================================================

    private record TlsTestServer(
            GatewayServer server,
            MainWorldChannelRegistry mainWorldChannels,
            ConnectedCharacterRegistry characterRegistry)
            implements AutoCloseable {
        @Override
        public void close() {
            server.stop();
            mainWorldChannels.stopAll();
            characterRegistry.stop();
        }
    }

    /** Bản sao độc lập của {@link #startServer()} — cùng cách wire facades, khác {@code tlsConfig}. */
    private TlsTestServer startServerWithTls(TlsConfig tlsConfig)
            throws InterruptedException, javax.net.ssl.SSLException {
        CharacterFacade characterFacade = fakeCharacterFacade();
        InventoryFacade inventoryFacade = fakeInventoryFacade();
        dev.kage.shop.api.ShopFacade shopFacade = fakeShopFacade();
        dev.kage.dialogue.api.DialogueFacade dialogueFacade = fakeDialogueFacade();
        dev.kage.quest.api.QuestFacade questFacade = fakeQuestFacade();
        dev.kage.friend.api.FriendFacade friendFacade = fakeFriendFacade();
        dev.kage.mail.api.MailFacade mailFacade = fakeMailFacade();
        dev.kage.chat.api.ChatFacade chatFacade = fakeChatFacade();
        dev.kage.guild.api.GuildFacade guildFacade = fakeGuildFacade();
        dev.kage.party.api.PartyFacade localPartyFacade = fakePartyFacade();
        dev.kage.trade.api.TradeFacade tradeFacade = fakeTradeFacade();
        dev.kage.pvp.api.PvpFacade localPvpFacade = fakePvpFacade();
        io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        GatewayNetworkMetrics networkMetrics = new GatewayNetworkMetrics(meterRegistry);
        GatewayObservability observability = new GatewayObservability(networkMetrics, GatewayTracing.disabled());
        ConnectedCharacterRegistry localCharacterRegistry = new ConnectedCharacterRegistry(
                inventoryFacade, questFacade, ExperienceGrantPort.disabled(), networkMetrics);
        WorldFacade firstChannelFacade = new WorldFacade(
                localCharacterRegistry, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        firstChannelFacade.start();
        MainWorldChannelRegistry localMainWorldChannels = new MainWorldChannelRegistry(
                localCharacterRegistry, firstChannelFacade, meterRegistry, TestWorldContents.trainingGroundWithElder());
        CharacterPositionPersister positionPersister =
                new CharacterPositionPersister(characterFacade, localCharacterRegistry, Duration.ofMinutes(5));
        DungeonInstanceRegistry dungeonInstanceRegistry = new DungeonInstanceRegistry(
                localCharacterRegistry,
                new DungeonInstanceRegistry.DungeonFacades(characterFacade, localPartyFacade),
                localMainWorldChannels,
                TestWorldContents.withBoss());
        ArenaInstanceRegistry arenaInstanceRegistry = new ArenaInstanceRegistry(
                localCharacterRegistry, characterFacade, localPvpFacade, localMainWorldChannels);
        WorldGateway worldGateway = new WorldGateway(
                localMainWorldChannels,
                localCharacterRegistry,
                positionPersister,
                dungeonInstanceRegistry,
                arenaInstanceRegistry);
        SocialFacades socialFacades = new SocialFacades(
                friendFacade, mailFacade, chatFacade, guildFacade, localPartyFacade, tradeFacade, localPvpFacade);
        ContentFacades contentFacades = new ContentFacades(shopFacade, dialogueFacade, questFacade, socialFacades);
        GatewayFacades facades =
                new GatewayFacades(fakeAccountFacade(), characterFacade, inventoryFacade, contentFacades);

        GatewayServer tlsServer =
                new GatewayServer(0, facades, new GatewayServer.GatewayContext(worldGateway, observability), tlsConfig);
        tlsServer.start();
        return new TlsTestServer(tlsServer, localMainWorldChannels, localCharacterRegistry);
    }

    @Test
    @Timeout(10)
    void shouldReceivePong_overTlsSocket_withPinnedCert() throws Exception {
        java.io.File certFile = testResourceFile("tls/dev-cert.pem");
        java.io.File keyFile = testResourceFile("tls/dev-key.pem");
        try (TlsTestServer tlsServer = startServerWithTls(new TlsConfig(true, certFile, keyFile))) {
            javax.net.ssl.SSLSocketFactory factory = pinnedTrustFactory("tls/dev-cert.pem");
            try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket)
                    factory.createSocket("127.0.0.1", tlsServer.server().port())) {
                socket.startHandshake();

                DataInputStream in = new DataInputStream(socket.getInputStream());
                sendPing(socket, 1, 999L);
                PacketCodec.Frame decoded = readFrame(in);

                assertThat(decoded.header().opcode()).isEqualTo(Opcode.OPCODE_PONG_VALUE);
                PongResponse pong = PongResponse.parseFrom(decoded.payload());
                assertThat(pong.getClientTimestampMs()).isEqualTo(999L);
            }
        }
    }

    @Test
    @Timeout(10)
    void shouldFailHandshake_whenClientDoesNotTrustServerCert() throws Exception {
        java.io.File certFile = testResourceFile("tls/dev-cert.pem");
        java.io.File keyFile = testResourceFile("tls/dev-key.pem");
        try (TlsTestServer tlsServer = startServerWithTls(new TlsConfig(true, certFile, keyFile))) {
            // TrustManager MẶC ĐỊNH của JDK (CA store hệ thống) — cert self-signed test KHÔNG nằm
            // trong đó, đúng hành vi "không pin thì không tin" (ADR-027).
            javax.net.ssl.SSLSocketFactory factory =
                    (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
            try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket)
                    factory.createSocket("127.0.0.1", tlsServer.server().port())) {
                org.assertj.core.api.Assertions.assertThatThrownBy(socket::startHandshake)
                        .isInstanceOf(javax.net.ssl.SSLHandshakeException.class);
            }
        }
    }

    @Test
    @Timeout(10)
    void shouldNotReturnValidResponse_whenClientSkipsTls() throws Exception {
        java.io.File certFile = testResourceFile("tls/dev-cert.pem");
        java.io.File keyFile = testResourceFile("tls/dev-key.pem");
        try (TlsTestServer tlsServer = startServerWithTls(new TlsConfig(true, certFile, keyFile))) {
            // Client plaintext gửi ĐÚNG format framing cũ — server giờ mong đợi TLS record ở byte
            // đầu tiên, không phải plaintext frame. Server KHÔNG được trả pong hợp lệ.
            try (Socket socket = new Socket(
                    InetAddress.getLoopbackAddress(), tlsServer.server().port())) {
                socket.setSoTimeout(3000);
                sendPing(socket, 1, 999L);

                org.assertj.core.api.Assertions.assertThatThrownBy(
                                () -> readFrame(new DataInputStream(socket.getInputStream())))
                        .as("server không được trả pong hợp lệ cho client bỏ qua TLS")
                        .isInstanceOf(Exception.class);
            }
        }
    }

    // ============================================================================================
    // OBSERVABILITY.md §6 — verify span con sâu THẬT SỰ được tạo (không chỉ đọc code) cho 3 lệnh
    // retrofit ở lát cắt này: BuyItemCommand/SellItemCommand/ConfirmTradeCommand. Dùng
    // GatewayTracing.forTesting(exporter) thay vì disabled() (mọi test khác ở file này) —
    // Sampler.alwaysOn() + SimpleSpanProcessor export ĐỒNG BỘ, không cần OTLP/Jaeger thật để verify
    // wiring. Dây OTLP→Jaeger thật đã verify tay qua `docker compose up` từ lát cắt gốc
    // (feature.inventory.enhance, Phase 9 lát cắt 10) — không lặp lại ở đây, chỉ verify phần MỚI.
    // ============================================================================================

    /** Exporter thu thập span vào bộ nhớ — thay OTLP thật, không cần Jaeger để verify wiring. */
    private static final class RecordingSpanExporter implements SpanExporter {
        private final List<SpanData> exported = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            exported.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        List<SpanData> exported() {
            return exported;
        }
    }

    private record TracingTestServer(
            GatewayServer server,
            MainWorldChannelRegistry mainWorldChannels,
            ConnectedCharacterRegistry characterRegistry)
            implements AutoCloseable {
        @Override
        public void close() {
            server.stop();
            mainWorldChannels.stopAll();
            characterRegistry.stop();
        }
    }

    /** Bản sao độc lập của {@link #startServer()} — khác {@code observability} (dùng tracing thật). */
    private TracingTestServer startServerWithTracing(GatewayTracing tracing)
            throws InterruptedException, javax.net.ssl.SSLException {
        CharacterFacade characterFacade = fakeCharacterFacade();
        InventoryFacade inventoryFacade = fakeInventoryFacade();
        dev.kage.shop.api.ShopFacade shopFacade = fakeShopFacade();
        dev.kage.dialogue.api.DialogueFacade dialogueFacade = fakeDialogueFacade();
        dev.kage.quest.api.QuestFacade questFacade = fakeQuestFacade();
        dev.kage.friend.api.FriendFacade friendFacade = fakeFriendFacade();
        dev.kage.mail.api.MailFacade mailFacade = fakeMailFacade();
        dev.kage.chat.api.ChatFacade chatFacade = fakeChatFacade();
        dev.kage.guild.api.GuildFacade guildFacade = fakeGuildFacade();
        dev.kage.party.api.PartyFacade localPartyFacade = fakePartyFacade();
        // Khác fakeTradeFacade() dùng ở nơi khác của file này — lockForExecution trả rỗng SẠCH
        // (NOT_FOUND) thay vì throw, vì test này THẬT SỰ gọi ConfirmTradeCommand tới hết luồng
        // (khác các test khác chỉ cần compile, không gọi confirm).
        dev.kage.trade.api.TradeFacade tradeFacade = fakeTradeFacadeAllowingConfirm();
        dev.kage.pvp.api.PvpFacade localPvpFacade = fakePvpFacade();
        io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        GatewayNetworkMetrics networkMetrics = new GatewayNetworkMetrics(meterRegistry);
        GatewayObservability observability = new GatewayObservability(networkMetrics, tracing);
        ConnectedCharacterRegistry localCharacterRegistry = new ConnectedCharacterRegistry(
                inventoryFacade, questFacade, ExperienceGrantPort.disabled(), networkMetrics);
        WorldFacade firstChannelFacade = new WorldFacade(
                localCharacterRegistry, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        firstChannelFacade.start();
        MainWorldChannelRegistry localMainWorldChannels = new MainWorldChannelRegistry(
                localCharacterRegistry, firstChannelFacade, meterRegistry, TestWorldContents.trainingGroundWithElder());
        CharacterPositionPersister positionPersister =
                new CharacterPositionPersister(characterFacade, localCharacterRegistry, Duration.ofMinutes(5));
        DungeonInstanceRegistry dungeonInstanceRegistry = new DungeonInstanceRegistry(
                localCharacterRegistry,
                new DungeonInstanceRegistry.DungeonFacades(characterFacade, localPartyFacade),
                localMainWorldChannels,
                TestWorldContents.withBoss());
        ArenaInstanceRegistry arenaInstanceRegistry = new ArenaInstanceRegistry(
                localCharacterRegistry, characterFacade, localPvpFacade, localMainWorldChannels);
        WorldGateway worldGateway = new WorldGateway(
                localMainWorldChannels,
                localCharacterRegistry,
                positionPersister,
                dungeonInstanceRegistry,
                arenaInstanceRegistry);
        SocialFacades socialFacades = new SocialFacades(
                friendFacade, mailFacade, chatFacade, guildFacade, localPartyFacade, tradeFacade, localPvpFacade);
        ContentFacades contentFacades = new ContentFacades(shopFacade, dialogueFacade, questFacade, socialFacades);
        GatewayFacades facades =
                new GatewayFacades(fakeAccountFacade(), characterFacade, inventoryFacade, contentFacades);

        GatewayServer tracingServer = new GatewayServer(
                0, facades, new GatewayServer.GatewayContext(worldGateway, observability), TlsConfig.disabled());
        tracingServer.start();
        return new TracingTestServer(tracingServer, localMainWorldChannels, localCharacterRegistry);
    }

    /** Cùng {@code fakeTradeFacade()} nhưng {@code lockForExecution} trả rỗng SẠCH (NOT_FOUND)
     * thay vì throw — cho phép test gọi {@code ConfirmTradeCommand} hết luồng thật. */
    private dev.kage.trade.api.TradeFacade fakeTradeFacadeAllowingConfirm() {
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
            public boolean replaceOffer(
                    long sessionId,
                    long characterId,
                    long goldAmount,
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
                // khác fakeTradeFacade() — cho phép confirm chạy hết luồng, trả NOT_FOUND SẠCH.
                return Optional.empty();
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
        dev.kage.trade.domain.TradeAuditPort auditPort = (tx, characterId, action, detailJson) -> {};
        dev.kage.trade.domain.IdempotencyPort idempotencyPort = (tx, idempotencyKey) -> {};
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

    /** Chờ (poll ngắn, KHÔNG {@code Thread.sleep} dài) tới khi {@code exporter} có span tên {@code spanName} —
     * {@code span.end()} chạy trên Virtual Thread khác thread test, cần chờ thay vì đọc ngay. */
    private static void awaitSpan(RecordingSpanExporter exporter, String spanName) throws InterruptedException {
        long deadlineMs = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadlineMs) {
            if (exporter.exported().stream().anyMatch(s -> s.getName().equals(spanName))) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Không thấy span \"" + spanName + "\" sau 5s — xem GatewayTracing/handle*Blocking");
    }

    @Test
    @Timeout(15)
    void clientCommands_shouldEmitDeepChildSpans_forGoldAffectingCommands() throws Exception {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        try (TracingTestServer tracingServer = startServerWithTracing(GatewayTracing.forTesting(exporter))) {
            try (Socket socket = new Socket(
                    InetAddress.getLoopbackAddress(), tracingServer.server().port())) {
                socket.setSoTimeout(5000);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                connectCreateAndSelect(
                        socket, in, "tracing" + UUID.randomUUID().toString().substring(0, 6));

                BuyItemCommand buyCommand = BuyItemCommand.newBuilder()
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .setItemTemplateId(1L)
                        .setQuantity(1)
                        .build();
                sendFrame(socket, Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, 20, buyCommand.toByteArray());
                readUntilOpcode(in, Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE);

                SellItemCommand sellCommand = SellItemCommand.newBuilder()
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .setItemId(1L)
                        .setQuantity(1)
                        .build();
                sendFrame(socket, Opcode.OPCODE_SELL_ITEM_COMMAND_VALUE, 21, sellCommand.toByteArray());
                readUntilOpcode(in, Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE);

                ConfirmTradeCommand confirmCommand = ConfirmTradeCommand.newBuilder()
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .setSessionId(1L)
                        .build();
                sendFrame(socket, Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE, 22, confirmCommand.toByteArray());
                ConfirmTradeResponse confirmResponse =
                        ConfirmTradeResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_CONFIRM_TRADE_RESPONSE_VALUE)
                                .payload());
                assertThat(confirmResponse.getOutcome()).isEqualTo(ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_NOT_FOUND);
            }

            awaitSpan(exporter, "feature.shop.buy");
            awaitSpan(exporter, "feature.shop.sell");
            awaitSpan(exporter, "feature.trade.confirm");

            assertThat(exporter.exported())
                    .as("cả 3 span đều phải là CON của span gateway.command (khớp pattern feature.inventory.enhance)")
                    .filteredOn(s -> s.getName().startsWith("feature."))
                    .allSatisfy(s -> assertThat(s.getParentSpanId()).isNotEqualTo("0000000000000000"));

            assertThat(exporter.exported())
                    .filteredOn(s -> s.getName().equals("feature.shop.buy"))
                    .singleElement()
                    .satisfies(s -> assertThat(s.getAttributes().get(AttributeKey.stringKey("result")))
                            .isEqualTo("ITEM_NOT_IN_SHOP"));
            assertThat(exporter.exported())
                    .filteredOn(s -> s.getName().equals("feature.shop.sell"))
                    .singleElement()
                    .satisfies(s -> assertThat(s.getAttributes().get(AttributeKey.stringKey("result")))
                            .isEqualTo("ITEM_NOT_FOUND"));
            assertThat(exporter.exported())
                    .filteredOn(s -> s.getName().equals("feature.trade.confirm"))
                    .singleElement()
                    .satisfies(s -> assertThat(s.getAttributes().get(AttributeKey.stringKey("result")))
                            .isEqualTo("NOT_FOUND"));
        }
    }

    private static java.io.File testResourceFile(String classpathPath) throws Exception {
        return new java.io.File(GatewayServerIntegrationTest.class
                .getClassLoader()
                .getResource(classpathPath)
                .toURI());
    }

    /** Xây {@link javax.net.ssl.SSLSocketFactory} PIN đúng 1 cert — cùng cơ chế client thật dùng (ADR-027). */
    private static javax.net.ssl.SSLSocketFactory pinnedTrustFactory(String certClasspathPath) throws Exception {
        java.security.cert.CertificateFactory certificateFactory =
                java.security.cert.CertificateFactory.getInstance("X.509");
        java.security.cert.Certificate pinnedCert;
        try (java.io.InputStream in =
                GatewayServerIntegrationTest.class.getClassLoader().getResourceAsStream(certClasspathPath)) {
            pinnedCert = certificateFactory.generateCertificate(in);
        }
        java.security.KeyStore trustStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("kage-server-test", pinnedCert);

        javax.net.ssl.TrustManagerFactory trustManagerFactory =
                javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext.getSocketFactory();
    }

    /** Người gửi luôn "đủ tiền" — test này không kiểm nghiệp vụ ký quỹ (xem MailIntegrationTest). */
    private static final class FakeMailGoldPort implements dev.kage.mail.domain.GoldPort {
        @Override
        public boolean deductGold(dev.kage.mail.domain.Transaction tx, long characterId, long amount) {
            return true;
        }

        @Override
        public void creditGold(dev.kage.mail.domain.Transaction tx, long characterId, long amount) {
            // không cần ghi nhận gì ở test này
        }
    }

    /** Túi đồ luôn "đủ chỗ và đủ món" — cùng lý do FakeMailGoldPort. */
    private static final class FakeMailInventoryGatewayPort implements dev.kage.mail.domain.InventoryGatewayPort {
        @Override
        public boolean grantItem(
                dev.kage.mail.domain.Transaction tx, long characterId, long itemTemplateId, int quantity) {
            return true;
        }

        @Override
        public boolean consumeItem(
                dev.kage.mail.domain.Transaction tx, long characterId, long itemTemplateId, int quantity) {
            return true;
        }
    }
}
