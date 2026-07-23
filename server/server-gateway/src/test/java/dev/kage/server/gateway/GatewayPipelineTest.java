package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
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
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.world.WorldFacade;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Test TOÀN BỘ pipeline (framing + codec + handler) qua {@link EmbeddedChannel} — không cần
 * socket thật (TESTING.md §2: "server-gateway: Netty client giả kết nối thật qua Netty
 * embedded"). Cùng cấu trúc pipeline với {@link GatewayBootstrap} — nếu hai bên lệch nhau, đây
 * là chỗ phải đồng bộ lại.
 */
class GatewayPipelineTest {

    @Test
    void shouldReplyPongToPing_withSameSequenceId() throws InvalidProtocolBufferException {
        var channel = newPipeline();
        var ping = PingCommand.newBuilder().setClientTimestampMs(123L).build();
        var header = new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, 7);

        writeInboundFrame(channel, header, ping.toByteArray());
        var response = readOutboundFrame(channel);

        // Response PHẢI khớp sequenceId của Command (NETWORK.md §105 request/response correlation).
        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_PONG_VALUE);
        assertThat(response.header().sequenceId()).isEqualTo(7);

        var pong = PongResponse.parseFrom(response.payload());
        assertThat(pong.getClientTimestampMs()).isEqualTo(123L);
        assertThat(pong.getServerTimestampMs()).isGreaterThan(0);
    }

    @Test
    void shouldReplyConnectResponse_toConnectRequest() throws InvalidProtocolBufferException {
        var channel = newPipeline();
        var request = ConnectRequest.newBuilder().setUsername("kagehero").build();
        var header = new PacketHeader(Opcode.OPCODE_CONNECT_REQUEST_VALUE, ProtocolVersion.CURRENT, 1);

        writeInboundFrame(channel, header, request.toByteArray());
        var response = readOutboundFrame(channel);

        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_CONNECT_RESPONSE_VALUE);
        var connectResponse = ConnectResponse.parseFrom(response.payload());
        assertThat(connectResponse.getSuccess()).isTrue();
        assertThat(connectResponse.getSessionId()).isNotBlank();
        assertThat(connectResponse.getAccountId()).isPositive();
    }

    @Test
    void shouldReplyError_forUnknownOpcode() {
        var channel = newPipeline();
        var header = new PacketHeader(9999, ProtocolVersion.CURRENT, 5);

        writeInboundFrame(channel, header, new byte[0]);
        var response = readOutboundFrame(channel);

        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    @Test
    void shouldRejectListCharacters_whenNotConnectedYet() {
        var channel = newPipeline();
        var header = new PacketHeader(Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, ProtocolVersion.CURRENT, 1);

        writeInboundFrame(channel, header, ListCharactersRequest.newBuilder().build().toByteArray());
        var response = readOutboundFrame(channel);

        // accountId gắn vào CONNECTION lúc Connect thành công (xem GatewayChannelHandler) — chưa
        // Connect thì chưa có accountId, phải từ chối chứ không đọc packet.
        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    @Test
    void shouldCreateThenListCharacter_afterConnect() throws InvalidProtocolBufferException {
        var channel = newPipeline();
        connect(channel, "kagehero2");

        var createRequest = CreateCharacterRequest.newBuilder()
                .setName("KageHero")
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 2),
                createRequest.toByteArray());
        var createResponseFrame = readOutboundFrame(channel);
        assertThat(createResponseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE);
        var createResponse = CreateCharacterResponse.parseFrom(createResponseFrame.payload());
        assertThat(createResponse.getSuccess()).isTrue();
        assertThat(createResponse.getCharacter().getName()).isEqualTo("KageHero");

        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, ProtocolVersion.CURRENT, 3),
                ListCharactersRequest.newBuilder().build().toByteArray());
        var listResponseFrame = readOutboundFrame(channel);
        assertThat(listResponseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE);
        var listResponse = ListCharactersResponse.parseFrom(listResponseFrame.payload());
        assertThat(listResponse.getCharactersList()).extracting(c -> c.getName()).containsExactly("KageHero");
    }

    @Test
    void shouldSelectCharacter_afterCreatingIt() throws InvalidProtocolBufferException {
        var channel = newPipeline();
        connect(channel, "selecttest1");
        var characterId = createCharacter(channel, "SelectHero", 2);

        var request = SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 3),
                request.toByteArray());
        var responseFrame = readOutboundFrame(channel);

        assertThat(responseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE);
        var response = SelectCharacterResponse.parseFrom(responseFrame.payload());
        assertThat(response.getSuccess()).isTrue();
    }

    @Test
    void shouldRejectSelectCharacter_whenBelongsToDifferentAccount() throws InvalidProtocolBufferException {
        var characterPort = new FakeCharacterPort();
        // Nhân vật thuộc account KHÁC — không tạo được qua chính kết nối test này (login luôn tạo
        // đúng account của username đang dùng) — seed thẳng vào Fake Port để mô phỏng đúng kịch
        // bản IDOR mà SECURITY.md §4 mô tả.
        var otherAccountCharacter =
                characterPort.create(null, new NewCharacter(999L, "OtherHero", Faction.HOA, "seed-idem"));

        var channel = newPipeline(characterPort);
        connect(channel, "selecttest2");

        var request = SelectCharacterRequest.newBuilder().setCharacterId(otherAccountCharacter.id()).build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 2),
                request.toByteArray());
        var responseFrame = readOutboundFrame(channel);

        assertThat(responseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    private static void connect(EmbeddedChannel channel, String username) {
        var request = ConnectRequest.newBuilder().setUsername(username).build();
        var header = new PacketHeader(Opcode.OPCODE_CONNECT_REQUEST_VALUE, ProtocolVersion.CURRENT, 1);
        writeInboundFrame(channel, header, request.toByteArray());
        readOutboundFrame(channel); // tiêu thụ ConnectResponse — caller không kiểm tra lại nội dung
    }

    private static long createCharacter(EmbeddedChannel channel, String name, int sequenceId)
            throws InvalidProtocolBufferException {
        var request = CreateCharacterRequest.newBuilder()
                .setName(name)
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        var header =
                new PacketHeader(Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, sequenceId);
        writeInboundFrame(channel, header, request.toByteArray());
        var response = CreateCharacterResponse.parseFrom(readOutboundFrame(channel).payload());
        return response.getCharacter().getId();
    }

    private static EmbeddedChannel newPipeline() {
        return newPipeline(new FakeCharacterPort());
    }

    // WorldFacade không start() ở test này (xem newPipeline) nên onMonsterKilled không bao giờ
    // thật sự bị gọi — Fake rỗng chỉ để thoả constructor, không kiểm tra hành vi ở đây.
    private static InventoryFacade fakeInventoryFacade() {
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

    // Shop không dùng trong test này — fake tối thiểu để compile (cùng convention fakeInventoryFacade).
    private static dev.kage.shop.api.ShopFacade fakeShopFacade() {
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

    // Dialogue không dùng trong test này — fake tối thiểu để compile (cùng convention fakeShopFacade).
    private static dev.kage.dialogue.api.DialogueFacade fakeDialogueFacade() {
        dev.kage.dialogue.domain.NpcTemplatePort npcTemplatePort = npcCode -> Optional.empty();
        var useCase = new dev.kage.dialogue.application.GetDialogueNodeUseCase(npcTemplatePort);
        return new dev.kage.dialogue.api.DialogueFacade(useCase);
    }

    // Quest không dùng trong test này — fake tối thiểu để compile (cùng convention fakeDialogueFacade).
    private static dev.kage.quest.api.QuestFacade fakeQuestFacade() {
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
    private static dev.kage.friend.api.FriendFacade fakeFriendFacade() {
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
    private static dev.kage.mail.api.MailFacade fakeMailFacade() {
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
    private static dev.kage.chat.api.ChatFacade fakeChatFacade() {
        dev.kage.chat.domain.ChatAuditPort chatAuditPort = (characterId, action, detailJson) -> { };
        return new dev.kage.chat.api.ChatFacade(new dev.kage.chat.application.SendChatMessageUseCase(chatAuditPort));
    }

    private static EmbeddedChannel newPipeline(FakeCharacterPort characterPort) {
        // ⚠️ Phải khớp CHÍNH XÁC pipeline thật trong GatewayBootstrap — lệch ở đây là test giả.
        // AccountFacade/CharacterFacade THẬT (không mock), chỉ Port bên dưới là Fake (TESTING.md
        // §4) — cùng kiểu Fake với *UseCaseTest ở feature-account/feature-character. Executor
        // chạy ĐỒNG BỘ (Runnable::run) — handler thật đẩy việc chạm DB sang authExecutor (Virtual
        // Thread ở production, SERVER.md §2); test cần đồng bộ để readOutboundFrame không bị race.
        // characterPort truyền vào từ ngoài để test seed sẵn dữ liệu (VD nhân vật account khác,
        // xem shouldRejectSelectCharacter_whenBelongsToDifferentAccount).
        var loginUseCase = new LoginOrCreateAccountUseCase(
                new FakeAccountPort(), new FakeSessionPort(), action -> action.accept(null));
        var accountFacade = new AccountFacade(loginUseCase);

        var createCharacterUseCase = new CreateCharacterUseCase(characterPort, action -> action.accept(null));
        var listCharactersUseCase = new ListCharactersUseCase(characterPort);
        var selectCharacterUseCase = new SelectCharacterUseCase(characterPort);
        var characterFacade = new CharacterFacade(
                createCharacterUseCase, listCharactersUseCase, selectCharacterUseCase, characterPort);

        // WorldFacade KHÔNG start() ở đây — test này không verify movement/AoI, chỉ cần
        // attachCharacter() (gọi trong handleSelectCharacterBlocking) không ném lỗi khi enqueue
        // vào hàng đợi chưa có ai drain (không tạo/rò rỉ thread tick cho mỗi test). Tương tự,
        // positionPersister không start() — save() (nếu bị gọi) chạy trực tiếp, không cần scheduler.
        var inventoryFacade = fakeInventoryFacade();
        var shopFacade = fakeShopFacade();
        var dialogueFacade = fakeDialogueFacade();
        var questFacade = fakeQuestFacade();
        var friendFacade = fakeFriendFacade();
        var mailFacade = fakeMailFacade();
        var chatFacade = fakeChatFacade();
        var characterRegistry = new ConnectedCharacterRegistry(inventoryFacade, questFacade);
        var worldFacade = new WorldFacade(characterRegistry);
        var positionPersister =
                new CharacterPositionPersister(characterFacade, characterRegistry, Duration.ofMinutes(5));
        var worldGateway = new WorldGateway(worldFacade, characterRegistry, positionPersister);

        return new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4),
                new LengthFieldPrepender(4),
                new PacketFrameDecoder(),
                new PacketFrameEncoder(),
                new GatewayChannelHandler(
                        new GatewayFacades(
                                accountFacade, characterFacade, inventoryFacade,
                                new ContentFacades(shopFacade, dialogueFacade, questFacade,
                                        new SocialFacades(friendFacade, mailFacade, chatFacade))),
                        worldGateway, Runnable::run));
    }

    /** Fake = implementation in-memory THẬT (TESTING.md §4), không Mockito. */
    static final class FakeAccountPort implements AccountPort {
        private final Map<String, Account> byUsername = new HashMap<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override
        public Account findOrCreate(Transaction tx, String username) {
            return byUsername.computeIfAbsent(username, u -> new Account(nextId.getAndIncrement(), u, Instant.now()));
        }
    }

    static final class FakeSessionPort implements SessionPort {
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
    }

    static final class FakeCharacterPort implements CharacterPort {
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

        // Số liệu khởi điểm PLACEHOLDER (khớp PostgresCharacterAdapter.STARTING_STAT) — test này
        // không verify chỉ số chiến đấu, chỉ cần handleSelectCharacterBlocking không lỗi.
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
    }

    private static void writeInboundFrame(EmbeddedChannel channel, PacketHeader header, byte[] payload) {
        byte[] frameBytes = PacketCodec.encodeFrame(header, payload);
        ByteBuf buf = Unpooled.buffer(4 + frameBytes.length);
        buf.writeInt(frameBytes.length);
        buf.writeBytes(frameBytes);
        channel.writeInbound(buf);
    }

    private static PacketCodec.Frame readOutboundFrame(EmbeddedChannel channel) {
        // ⚠️ PHÁT HIỆN THẬT: LengthFieldPrepender là MessageToMessageEncoder<ByteBuf> — encode()
        // sinh ra HAI message riêng biệt (buffer length-prefix + buffer content gốc qua retain()),
        // không gộp thành 1 buffer. Qua socket TCP thật thì vô hình (TCP là stream, tự nối lại),
        // nhưng EmbeddedChannel.readOutbound() trả về từng message một lần gọi — phải gom đủ.
        ByteBuf combined = Unpooled.buffer();
        ByteBuf part;
        while ((part = channel.readOutbound()) != null) {
            combined.writeBytes(part);
            part.release();
        }
        assertThat(combined.readableBytes())
                .as("server phải trả lời — không có outbound message nào")
                .isGreaterThan(0);
        int length = combined.readInt();
        byte[] bytes = new byte[length];
        combined.readBytes(bytes);
        return PacketCodec.decodeFrame(bytes);
    }
}
