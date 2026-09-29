package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
import dev.kage.protocol.BuyItemCommand;
import dev.kage.protocol.BuyItemResponse;
import dev.kage.protocol.BuyOutcome;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageOutcome;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Thay thế DAST tiêu chuẩn (ZAP/Burp vô dụng với TCP+Protobuf nhị phân — OWASP.md §4) bằng test tự
 * viết tấn công server THẬT qua socket thật, cùng pattern {@link GatewayServerIntegrationTest}.
 * 4 mảng, mỗi mảng verify đúng 1 câu hỏi OWASP.md §4 đặt ra:
 *
 * <ul>
 *   <li><b>IDOR/Authorization</b> — {@code characterId} có PHẢI lấy từ session, không phải packet
 *       không (SECURITY.md §4)? {@link SelectCharacterRequest} là packet DUY NHẤT thật sự mang
 *       {@code characterId} do client cung cấp (mọi Command khác dùng {@code boundCharacterId} từ
 *       session, không có field id trong packet) — đây là bề mặt IDOR CÓ THẬT, không phải giả định.
 *   <li><b>Replay</b> — {@code idempotencyKey} (ADR-025) có chặn double-effect thật không? Và
 *       {@code sequenceId} (NETWORK.md §5) — dùng để chặn thật hay chỉ để khớp request/response?
 *   <li><b>Rate limit</b> — {@link RateLimiter} có thật sự chặn ở tầng dây, tăng
 *       {@code kage.ratelimit.rejected} không, hay chỉ đúng khi test đơn vị cô lập?
 *   <li><b>Fuzzing</b> — packet dị dạng/ngẫu nhiên có làm server crash/treo không?
 * </ul>
 */
class SecurityIntegrationTest {

    private static final long SHOP_ITEM_TEMPLATE_ID = 1L;
    private static final long SHOP_BUY_PRICE = 10L;
    private static final long SHOP_STARTING_GOLD = 1_000_000L;
    private static final int FUZZ_ITERATIONS = 36;

    private GatewayServer server;
    private MainWorldChannelRegistry mainWorldChannels;
    private ConnectedCharacterRegistry characterRegistry;
    private io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry;
    private Map<Long, Long> shopGoldBalances;
    private AtomicInteger shopDeductCallCount;
    private AtomicInteger shopGrantCallCount;

    @BeforeEach
    void startServer() throws InterruptedException, javax.net.ssl.SSLException {
        CharacterFacade characterFacade = fakeIdempotentCharacterFacade();
        InventoryFacade inventoryFacade = fakeInventoryFacade();
        dev.kage.shop.api.ShopFacade shopFacade = fakeShopFacadeWithRealListing();
        dev.kage.dialogue.api.DialogueFacade dialogueFacade = fakeDialogueFacade();
        dev.kage.quest.api.QuestFacade questFacade = fakeQuestFacade();
        dev.kage.friend.api.FriendFacade friendFacade = fakeFriendFacade();
        dev.kage.mail.api.MailFacade mailFacade = fakeMailFacade();
        dev.kage.chat.api.ChatFacade chatFacade = fakeChatFacade();
        dev.kage.guild.api.GuildFacade guildFacade = fakeGuildFacade();
        dev.kage.party.api.PartyFacade partyFacade = fakePartyFacade();
        dev.kage.trade.api.TradeFacade tradeFacade = fakeTradeFacade();
        dev.kage.pvp.api.PvpFacade pvpFacade = fakePvpFacade();
        meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        GatewayNetworkMetrics networkMetrics = new GatewayNetworkMetrics(meterRegistry);
        GatewayObservability observability = new GatewayObservability(networkMetrics, GatewayTracing.disabled());
        characterRegistry = new ConnectedCharacterRegistry(
                inventoryFacade, questFacade, ExperienceGrantPort.disabled(), networkMetrics);
        WorldFacade firstChannelFacade = new WorldFacade(characterRegistry);
        firstChannelFacade.start();
        mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry, firstChannelFacade, meterRegistry, WorldContentCatalog.EMPTY);
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
        server = new GatewayServer(
                0, facades, new GatewayServer.GatewayContext(worldGateway, observability), TlsConfig.disabled());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    // ============================================================================================
    // 1. AUTHORIZATION / IDOR (OWASP.md §4 TOC mục 2, SECURITY.md §4)
    // ============================================================================================

    @Test
    @Timeout(10)
    void shouldRejectAuthenticatedCommand_beforeAnyLogin() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // KHÔNG gửi ConnectRequest — channel vừa mở, boundAccountId/boundCharacterId đều null.
            MoveCommand moveCommand =
                    MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 1, moveCommand.toByteArray());

            ErrorResponse response = ErrorResponse.parseFrom(readFrame(in).payload());
            assertThat(response.getErrorCode()).isEqualTo(401);
            assertThat(response.getErrorKey()).isEqualTo("error.not_authenticated");
        }
    }

    @Test
    @Timeout(10)
    void shouldRejectAuthenticatedCommand_afterLoginButBeforeCharacterSelected() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            ConnectRequest connectRequest = ConnectRequest.newBuilder()
                    .setUsername("nopick" + UUID.randomUUID().toString().substring(0, 6))
                    .build();
            sendFrame(socket, Opcode.OPCODE_CONNECT_REQUEST_VALUE, 1, connectRequest.toByteArray());
            ConnectResponse.parseFrom(readFrame(in).payload());

            // Đăng nhập THÀNH CÔNG nhưng chưa SelectCharacterRequest — boundCharacterId vẫn null.
            MoveCommand moveCommand =
                    MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 2, moveCommand.toByteArray());
            ErrorResponse response = ErrorResponse.parseFrom(readFrame(in).payload());
            assertThat(response.getErrorCode()).isEqualTo(401);
            assertThat(response.getErrorKey()).isEqualTo("error.not_authenticated");
        }
    }

    @Test
    @Timeout(15)
    void shouldRejectSelectCharacter_whenCharacterBelongsToAnotherAccount() throws Exception {
        try (Socket socketA = new Socket("127.0.0.1", server.port());
                Socket socketB = new Socket("127.0.0.1", server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            DataInputStream inA = new DataInputStream(socketA.getInputStream());
            DataInputStream inB = new DataInputStream(socketB.getInputStream());
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            long characterA = connectAndCreateCharacterOnly(socketA, inA, "idorA" + suffix);
            connectAndCreateCharacterOnly(socketB, inB, "idorB" + suffix); // account B's own char — không dùng.

            // B (account KHÁC A) cố SelectCharacterRequest với characterId của A. Đây là bề mặt IDOR
            // CÓ THẬT trong protocol này (khác mọi Command khác — SelectCharacterRequest là packet
            // DUY NHẤT client được tự khai characterId), xem SelectCharacterUseCase.execute()
            // (feature-character) — filter theo listByAccount(accountId TỪ SESSION) trước khi trả.
            SelectCharacterRequest maliciousSelect = SelectCharacterRequest.newBuilder()
                    .setCharacterId(characterA)
                    .build();
            sendFrame(socketB, Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, 3, maliciousSelect.toByteArray());
            ErrorResponse errorResponse = ErrorResponse.parseFrom(readFrame(inB).payload());
            assertThat(errorResponse.getErrorCode()).isEqualTo(404);
            // Cùng lỗi dù nhân vật không tồn tại hay thuộc account khác (fail-secure, không lộ
            // "characterId này CÓ tồn tại nhưng không phải của bạn" — SECURITY.md §4).
            assertThat(errorResponse.getErrorKey()).isEqualTo("error.character_not_found");

            // Session của B KHÔNG bị bind vào nhân vật của A — MoveCommand ngay sau đó vẫn 401,
            // chứng minh lần select trái phép không để lại tác dụng phụ nào trên session.
            MoveCommand moveCommand =
                    MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socketB, Opcode.OPCODE_MOVE_COMMAND_VALUE, 4, moveCommand.toByteArray());
            ErrorResponse moveError = ErrorResponse.parseFrom(readFrame(inB).payload());
            assertThat(moveError.getErrorKey()).isEqualTo("error.not_authenticated");
        }
    }

    @Test
    @Timeout(15)
    void shouldAcceptSelectCharacter_whenCharacterBelongsToOwnAccount() throws Exception {
        // Đối chứng dương cho shouldRejectSelectCharacter_whenCharacterBelongsToAnotherAccount —
        // cùng packet, cùng field characterId do client khai, nhưng khai ĐÚNG nhân vật CỦA MÌNH.
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "idorown" + UUID.randomUUID().toString().substring(0, 6));

            MoveCommand moveCommand =
                    MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());
            MoveResponse moveResponse = MoveResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE).payload());
            assertThat(moveResponse.getX()).isGreaterThanOrEqualTo(0f);
        }
    }

    // ============================================================================================
    // 2. REPLAY (OWASP.md §4 TOC mục 3, ADR-025, NETWORK.md §5-6)
    // ============================================================================================

    @Test
    @Timeout(15)
    void shouldRejectDuplicateBuyItemCommand_whenIdempotencyKeyReused() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            long characterId = connectCreateAndSelect(
                    socket, in, "buyreplay" + UUID.randomUUID().toString().substring(0, 6));

            String idempotencyKey = UUID.randomUUID().toString();
            BuyItemCommand buyCommand = BuyItemCommand.newBuilder()
                    .setItemTemplateId(SHOP_ITEM_TEMPLATE_ID)
                    .setQuantity(2)
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            sendFrame(socket, Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, 10, buyCommand.toByteArray());
            BuyItemResponse firstResponse = BuyItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE).payload());
            assertThat(firstResponse.getOutcome()).isEqualTo(BuyOutcome.BUY_OUTCOME_OK);
            assertThat(shopDeductCallCount.get()).isEqualTo(1);
            assertThat(shopGrantCallCount.get()).isEqualTo(1);
            long balanceAfterFirstBuy = shopGoldBalances.get(characterId);
            assertThat(balanceAfterFirstBuy).isEqualTo(SHOP_STARTING_GOLD - SHOP_BUY_PRICE * 2);

            // Client tự retry Y HỆT request (mất kết nối trước khi nhận response, không biết server
            // đã xử lý chưa — NETWORK.md §5) — idempotencyKey GIỮ NGUYÊN, đúng kịch bản ADR-025.
            sendFrame(socket, Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, 11, buyCommand.toByteArray());
            BuyItemResponse secondResponse = BuyItemResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE).payload());
            assertThat(secondResponse.getOutcome()).isEqualTo(BuyOutcome.BUY_OUTCOME_DUPLICATE_REQUEST);
            // Hiệu ứng KHÔNG lặp lại — vẫn đúng 1 lần trừ tiền, 1 lần cấp item.
            assertThat(shopDeductCallCount.get()).isEqualTo(1);
            assertThat(shopGrantCallCount.get()).isEqualTo(1);
            assertThat(shopGoldBalances.get(characterId)).isEqualTo(balanceAfterFirstBuy);
        }
    }

    @Test
    @Timeout(15)
    void shouldAllowBuyItemCommand_whenIdempotencyKeyDiffers() throws Exception {
        // Đối chứng cho test trên — chứng minh việc chặn KHÔNG PHẢI vì "mua item giống hệt lần 2"
        // (VD 1 dạng dedupe theo nội dung), mà cụ thể là VÌ idempotencyKey trùng.
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            long characterId = connectCreateAndSelect(
                    socket, in, "buytwice" + UUID.randomUUID().toString().substring(0, 6));

            BuyItemCommand firstBuy = BuyItemCommand.newBuilder()
                    .setItemTemplateId(SHOP_ITEM_TEMPLATE_ID)
                    .setQuantity(1)
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .build();
            sendFrame(socket, Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, 10, firstBuy.toByteArray());
            assertThat(BuyItemResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE)
                                    .payload())
                            .getOutcome())
                    .isEqualTo(BuyOutcome.BUY_OUTCOME_OK);

            BuyItemCommand secondBuy = BuyItemCommand.newBuilder()
                    .setItemTemplateId(SHOP_ITEM_TEMPLATE_ID)
                    .setQuantity(1)
                    .setIdempotencyKey(UUID.randomUUID().toString()) // KHÁC key — mua lần 2 THẬT.
                    .build();
            sendFrame(socket, Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, 11, secondBuy.toByteArray());
            assertThat(BuyItemResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE)
                                    .payload())
                            .getOutcome())
                    .isEqualTo(BuyOutcome.BUY_OUTCOME_OK);

            assertThat(shopDeductCallCount.get()).isEqualTo(2);
            assertThat(shopGrantCallCount.get()).isEqualTo(2);
            assertThat(shopGoldBalances.get(characterId)).isEqualTo(SHOP_STARTING_GOLD - SHOP_BUY_PRICE * 2);
        }
    }

    @Test
    @Timeout(15)
    void shouldPreventDuplicateCharacterCreation_whenRetryingIdenticalRequest() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            ConnectRequest connectRequest = ConnectRequest.newBuilder()
                    .setUsername("createreplay" + UUID.randomUUID().toString().substring(0, 6))
                    .build();
            sendFrame(socket, Opcode.OPCODE_CONNECT_REQUEST_VALUE, 1, connectRequest.toByteArray());
            ConnectResponse.parseFrom(readFrame(in).payload());

            String name = "Hero" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String idempotencyKey = UUID.randomUUID().toString();
            CreateCharacterRequest createRequest = CreateCharacterRequest.newBuilder()
                    .setName(name)
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            sendFrame(socket, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 2, createRequest.toByteArray());
            CreateCharacterResponse firstResponse =
                    CreateCharacterResponse.parseFrom(readFrame(in).payload());
            assertThat(firstResponse.getSuccess()).isTrue();

            // Client tự retry Y HỆT request (tên + idempotencyKey giữ nguyên — client mất kết nối
            // trước khi nhận response lần đầu). PHÁT HIỆN THẬT: CreateCharacterUseCase.execute()
            // kiểm existsByName() TRƯỚC KHI transaction chạm idempotencyKey (thứ tự có sẵn trong
            // code, không phải phát sinh từ test) — nên 1 retry Y HỆT (tên giống) bị chặn bởi lỗi
            // TRÙNG TÊN, KHÔNG PHẢI error.duplicate_request, dù hiệu ứng cuối (không tạo nhân vật
            // thứ 2) vẫn đúng. idempotencyKey THẬT SỰ phát huy tác dụng khi tên khác nhau — xem
            // shouldRejectCreateCharacterCommand_whenIdempotencyKeyReused_withDifferentName bên dưới.
            sendFrame(socket, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 3, createRequest.toByteArray());
            ErrorResponse retryError = ErrorResponse.parseFrom(readFrame(in).payload());
            assertThat(retryError.getErrorKey()).isEqualTo("error.duplicate_character_name");

            ListCharactersRequest listRequest =
                    ListCharactersRequest.newBuilder().build();
            sendFrame(socket, Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, 4, listRequest.toByteArray());
            ListCharactersResponse listResponse =
                    ListCharactersResponse.parseFrom(readFrame(in).payload());
            assertThat(listResponse.getCharactersList()).hasSize(1);
        }
    }

    @Test
    @Timeout(15)
    void shouldRejectCreateCharacterCommand_whenIdempotencyKeyReused_withDifferentName() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            ConnectRequest connectRequest = ConnectRequest.newBuilder()
                    .setUsername("createreplay2" + UUID.randomUUID().toString().substring(0, 6))
                    .build();
            sendFrame(socket, Opcode.OPCODE_CONNECT_REQUEST_VALUE, 1, connectRequest.toByteArray());
            ConnectResponse.parseFrom(readFrame(in).payload());

            String idempotencyKey = UUID.randomUUID().toString();
            String firstName =
                    "Hero" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            CreateCharacterRequest firstCreate = CreateCharacterRequest.newBuilder()
                    .setName(firstName)
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            sendFrame(socket, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 2, firstCreate.toByteArray());
            assertThat(CreateCharacterResponse.parseFrom(readFrame(in).payload())
                            .getSuccess())
                    .isTrue();

            // Tên KHÁC (né existsByName), idempotencyKey GIỐNG — bây giờ mới thật sự CHẠM cơ chế
            // ADR-025 (khác test ở trên, nơi lỗi trùng tên chặn TRƯỚC khi tới bước idempotency).
            String secondName =
                    "Hero" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            CreateCharacterRequest secondCreate = CreateCharacterRequest.newBuilder()
                    .setName(secondName)
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            sendFrame(socket, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 3, secondCreate.toByteArray());
            ErrorResponse secondError = ErrorResponse.parseFrom(readFrame(in).payload());
            assertThat(secondError.getErrorKey()).isEqualTo("error.duplicate_request");

            ListCharactersRequest listRequest =
                    ListCharactersRequest.newBuilder().build();
            sendFrame(socket, Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, 4, listRequest.toByteArray());
            ListCharactersResponse listResponse =
                    ListCharactersResponse.parseFrom(readFrame(in).payload());
            assertThat(listResponse.getCharactersList()).hasSize(1);
        }
    }

    @Test
    @Timeout(15)
    void moveCommand_sequenceIdIsNotEnforcedAsReplayGuard_documentedGap() throws Exception {
        // PHÁT HIỆN THẬT: NETWORK.md §6 liệt kê "sequenceId tăng dần — server từ chối sequenceId ≤
        // cái đã thấy" như 1 cơ chế chống replay, nhưng GatewayChannelHandler KHÔNG hề implement
        // việc này ở bất kỳ đâu (không có state lưu "sequenceId cuối đã thấy" cho MoveCommand/
        // SetTargetCommand/UseSkillCommand...) — sequenceId CHỈ dùng để khớp request/response
        // (NETWORK.md §5), không phải replay guard. Test này KHÔNG assert 1 lỗ hổng bị khai thác
        // được (MoveCommand tự nó không có side-effect tài chính để "double" — chỉ đổi hướng đi),
        // mà xác nhận TRUNG THỰC hiện trạng: gửi sequenceId THẤP HƠN vẫn được xử lý bình thường,
        // không hề bị từ chối như tài liệu mô tả. Command CÓ side-effect thật (Buy/Sell/CreateChar/
        // ClaimMail/ConfirmTrade) được bảo vệ bởi idempotencyKey RIÊNG (test ở trên), không phải
        // cơ chế sequenceId này.
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "seqgap" + UUID.randomUUID().toString().substring(0, 6));

            MoveCommand moveHigh =
                    MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 100, moveHigh.toByteArray());
            MoveResponse responseHigh = MoveResponse.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE).payload());
            assertThat(responseHigh).isNotNull();

            // sequenceId=1 THẤP HƠN 100 đã gửi — nếu server có replay guard theo sequenceId, gói
            // này phải bị từ chối/bỏ qua. Thực tế: vẫn nhận được MoveResponse với ĐÚNG sequenceId=1,
            // chứng minh KHÔNG có guard nào chặn.
            MoveCommand moveStale =
                    MoveCommand.newBuilder().setDirX(0f).setDirY(1f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 1, moveStale.toByteArray());
            PacketCodec.Frame staleFrame = readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE);
            assertThat(staleFrame.header().sequenceId()).isEqualTo(1);
        }
    }

    // ============================================================================================
    // 3. RATE LIMIT (OWASP.md §4 TOC mục 3, NETWORK.md §13)
    // ============================================================================================

    @Test
    @Timeout(10)
    void shouldRejectMoveCommands_pastRateLimitThreshold_andIncrementMetric() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "moveflood" + UUID.randomUUID().toString().substring(0, 6));

            // Ngưỡng THẬT: 30/giây (MAX_MOVE_COMMANDS_PER_SECOND, GatewayChannelHandler) — flood
            // theo THỜI GIAN (không phải số lượng cố định): máy chạy chậm/tải nặng (VD giữa lúc
            // `./gradlew build` chạy CẢ bộ test) có thể khiến 1 vòng lặp N lần cố định KHÔNG kịp
            // nằm gọn trong 1 window 1s (PHÁT HIỆN THẬT — bản đầu dùng "for i<60" bị flaky khi chạy
            // trong bộ đầy đủ, dù luôn pass khi chạy đơn lẻ). Flood liên tục 1.5s đảm bảo LUÔN có
            // ít nhất 1 window vượt ngưỡng bất kể tốc độ máy.
            MoveCommand moveCommand =
                    MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            byte[] payload = moveCommand.toByteArray();
            Instant deadline = Instant.now().plusMillis(1500);
            int sequenceId = 100;
            while (Instant.now().isBefore(deadline)) {
                sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, sequenceId++, payload);
            }

            double rejectedCount = meterRegistry
                    .get("kage.ratelimit.rejected")
                    .tag("scope", "move")
                    .counter()
                    .count();
            assertThat(rejectedCount).isGreaterThan(0);
        }
    }

    @Test
    @Timeout(20)
    void shouldDisconnectConnection_whenMoveRateLimitViolatedForSustainedPeriod() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "movekick" + UUID.randomUUID().toString().substring(0, 6));

            MoveCommand moveCommand =
                    MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            byte[] payload = moveCommand.toByteArray();
            // Flood LIÊN TỤC ~3.5s (window=1s, cần 3 window LIÊN TIẾP vượt ngưỡng để server tự ngắt
            // — RateLimiter.SUSTAINED_VIOLATION_WINDOWS=3) — không để khoảng trống nào giữa các lần
            // gửi để mỗi window đều thật sự vượt ngưỡng.
            Instant deadline = Instant.now().plusMillis(3500);
            int sequenceId = 0;
            while (Instant.now().isBefore(deadline)) {
                try {
                    sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, sequenceId++, payload);
                } catch (IOException writeFailed) {
                    break; // server đã đóng kết nối giữa chừng — dừng sớm, xác nhận lại ở bước dưới.
                }
            }

            double rejectedCount = meterRegistry
                    .get("kage.ratelimit.rejected")
                    .tag("scope", "move")
                    .counter()
                    .count();
            assertThat(rejectedCount).isGreaterThan(0);

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
                        sendFrame(
                                socket,
                                Opcode.OPCODE_PING_VALUE,
                                999999,
                                PingCommand.newBuilder()
                                        .setClientTimestampMs(1L)
                                        .build()
                                        .toByteArray());
                        readFrame(in);
                    })
                    .as("server phải đã đóng kết nối sau 3 window liên tiếp vượt rate limit")
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    @Timeout(15)
    void shouldRejectChatCommands_pastRateLimitThreshold_andRespondRateLimited() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            connectCreateAndSelect(
                    socket, in, "chatflood" + UUID.randomUUID().toString().substring(0, 6));

            // Ngưỡng THẬT: 5 tin/10s cho kênh WORLD (chatWorldRateLimiter, nghiêm ngặt nhất trong
            // các kênh chat — GAMEPLAY.md §10).
            for (int i = 1; i <= 6; i++) {
                SendChatMessageCommand command = SendChatMessageCommand.newBuilder()
                        .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                        .setBody("flood " + i)
                        .build();
                sendFrame(socket, Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, i, command.toByteArray());
            }

            // 5 tin đầu xử lý BẤT ĐỒNG BỘ (authExecutor/Virtual Thread), tin thứ 6 bị chặn NGAY
            // trên EventLoop (đồng bộ) — thứ tự phản hồi tới KHÔNG đảm bảo theo thứ tự gửi, khớp
            // theo sequenceId thay vì theo thứ tự đọc được.
            Map<Integer, SendChatMessageOutcome> outcomeBySequenceId = new HashMap<Integer, SendChatMessageOutcome>();
            for (int i = 1; i <= 6; i++) {
                PacketCodec.Frame frame = readUntilOpcode(in, Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE);
                SendChatMessageResponse response = SendChatMessageResponse.parseFrom(frame.payload());
                outcomeBySequenceId.put(frame.header().sequenceId(), response.getOutcome());
            }

            for (int seq = 1; seq <= 5; seq++) {
                assertThat(outcomeBySequenceId.get(seq)).isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK);
            }
            assertThat(outcomeBySequenceId.get(6))
                    .isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_RATE_LIMITED);

            double rejectedCount = meterRegistry
                    .get("kage.ratelimit.rejected")
                    .tag("scope", "chat_CHAT_CHANNEL_WORLD")
                    .counter()
                    .count();
            assertThat(rejectedCount).isGreaterThan(0);
        }
    }

    // ============================================================================================
    // 4. PACKET FUZZING (OWASP.md §4 TOC mục 1)
    // ============================================================================================

    // PMD AvoidCatchingGenericException/EmptyCatchBlock: ĐÚNG Ý của fuzz test — mục tiêu DUY NHẤT
    // là "server không crash/treo", không phải nội dung response của TỪNG biến thể dị dạng. Nhiều
    // biến thể CHỦ Ý không có response hợp lệ (đóng kết nối/timeout) — đó chính là hành vi mong đợi
    // (Điều 16 fail-fast), không phải lỗi bị nuốt âm thầm. Assertion THẬT nằm ở sanity check cuối
    // cùng: server còn sống, xử lý ĐÚNG cho 1 kết nối mới hoàn toàn sạch.
    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.EmptyCatchBlock"})
    @Test
    @Timeout(60)
    void shouldNotCrashOrHang_whenReceivingMalformedOrRandomFrames() throws Exception {
        Random random = new Random(20260802L); // seed cố định — tái lập được khi cần điều tra lại.
        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            int variant = i % 6;
            try (Socket socket = new Socket("127.0.0.1", server.port())) {
                socket.setSoTimeout(1000);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                switch (variant) {
                    case 0 ->
                        // Header hợp lệ (opcode PING thật), payload protobuf RÁC.
                        sendFrame(socket, Opcode.OPCODE_PING_VALUE, i, randomBytes(random, 1 + random.nextInt(80)));
                    case 1 ->
                        // Opcode KHÔNG tồn tại trong Opcode registry (giả mạo).
                        sendFrame(socket, 60000 + random.nextInt(5000), i, randomBytes(random, random.nextInt(50)));
                    case 2 ->
                        // Frame ngắn hơn HEADER_SIZE(7) — PacketCodec.decodeFrame ném IllegalArgumentException.
                        writeRawFrame(out, 3, randomBytes(random, 3));
                    case 3 ->
                        // Frame rỗng (length=0) — cùng nhánh lỗi decoder ở trên, edge case riêng.
                        writeRawFrame(out, 0, new byte[0]);
                    case 4 ->
                        // Khai length > MAX_FRAME_LENGTH (1MB) — TooLongFrameException, KHÔNG cần
                        // gửi đủ byte (LengthFieldBasedFrameDecoder failFast ngay khi đọc length field).
                        writeRawFrame(out, 5_000_000, new byte[0]);
                    default ->
                        // Khai 20 byte, chỉ gửi 5 rồi đóng kết nối — mô phỏng client rớt mạng giữa chừng.
                        writeRawFrame(out, 20, randomBytes(random, 5));
                }
                try {
                    readFrame(new DataInputStream(socket.getInputStream()));
                } catch (Exception readOutcome) {
                    // Mong đợi cho nhiều biến thể (không response hợp lệ/kết nối đã đóng).
                }
            } catch (Exception connectionOutcome) {
                // Mong đợi cho biến thể làm rớt kết nối (framing lỗi/TooLongFrame).
            }
        }

        // Sanity CUỐI — server vẫn sống, xử lý ĐÚNG cho 1 kết nối MỚI, hoàn toàn sạch.
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            sendPing(socket, 1, 42L);
            PongResponse pong = PongResponse.parseFrom(readFrame(in).payload());
            assertThat(pong.getClientTimestampMs()).isEqualTo(42L);
        }
    }

    private static byte[] randomBytes(Random random, int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private static void writeRawFrame(DataOutputStream out, int declaredLength, byte[] actualBodyBytes)
            throws IOException {
        out.writeInt(declaredLength);
        out.write(actualBodyBytes);
        out.flush();
    }

    // ============================================================================================
    // Fake facade — CharacterPort mô phỏng ĐÚNG contract idempotency thật (PostgresCharacterAdapter,
    // ADR-025): vi phạm unique idempotencyKey trong CÙNG "transaction" với insert -> ném
    // IdempotencyKeyReusedException, KHÔNG tạo nhân vật. Fake gốc GatewayServerIntegrationTest
    // KHÔNG cần mô phỏng phần này (không test replay) — file này cần THẬT để verify ADR-025.
    // ============================================================================================

    private CharacterFacade fakeIdempotentCharacterFacade() {
        CharacterPort characterPort = new CharacterPort() {
            private final List<Character> characters = new ArrayList<Character>();
            private final AtomicLong nextId = new AtomicLong(1);
            private final Map<Long, PositionSnapshot> positions = new HashMap<Long, PositionSnapshot>();
            private final Set<String> usedIdempotencyKeys = ConcurrentHashMap.newKeySet();

            @Override
            public PositionSnapshot loadPosition(long characterId) {
                return positions.getOrDefault(characterId, new PositionSnapshot(0f, 0f));
            }

            @Override
            public void savePosition(long characterId, float x, float y) {
                positions.put(characterId, new PositionSnapshot(x, y));
            }

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
                if (!usedIdempotencyKeys.add(newCharacter.idempotencyKey())) {
                    throw new dev.kage.character.domain.IdempotencyKeyReusedException(newCharacter.idempotencyKey());
                }
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

            @Override
            public boolean saveLevel(long characterId, int level, long derivedFromExp) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public void updateFaction(long characterId, Faction faction) {
                // Không dùng ở file này.
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

    private static AccountFacade fakeAccountFacade() {
        AccountPort accountPort = new AccountPort() {
            private final Map<String, Account> byUsername = new HashMap<String, Account>();
            private final AtomicLong nextId = new AtomicLong(1);

            @Override
            public Account findOrCreate(Transaction tx, String username) {
                return byUsername.computeIfAbsent(
                        username, u -> new Account(nextId.getAndIncrement(), u, Instant.now()));
            }
        };
        SessionPort sessionPort = new SessionPort() {
            private final Map<String, Long> tokens = new HashMap<String, Long>();

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

    // ============================================================================================
    // Fake facade — ShopFacade với 1 listing THẬT + GoldPort/InventoryGatewayPort/IdempotencyPort
    // có TRẠNG THÁI thật (đếm lời gọi, số dư gold) — khác fakeShopFacade() rỗng của
    // GatewayServerIntegrationTest (chỉ cần compile) — file này cần chứng minh hiệu ứng KHÔNG lặp
    // lại (gold trừ đúng 1 lần, item cấp đúng 1 lần) khi replay.
    // ============================================================================================

    private dev.kage.shop.api.ShopFacade fakeShopFacadeWithRealListing() {
        shopGoldBalances = new ConcurrentHashMap<Long, Long>();
        shopDeductCallCount = new AtomicInteger();
        shopGrantCallCount = new AtomicInteger();
        Set<String> usedShopIdempotencyKeys = ConcurrentHashMap.newKeySet();

        dev.kage.shop.domain.ShopCatalogPort catalogPort = new dev.kage.shop.domain.ShopCatalogPort() {
            @Override
            public Optional<dev.kage.shop.domain.ShopListing> findListing(long itemTemplateId) {
                if (itemTemplateId != SHOP_ITEM_TEMPLATE_ID) {
                    return Optional.empty();
                }
                return Optional.of(new dev.kage.shop.domain.ShopListing(
                        SHOP_ITEM_TEMPLATE_ID, SHOP_BUY_PRICE, SHOP_BUY_PRICE / 2));
            }

            @Override
            public List<dev.kage.shop.domain.ShopListing> findAll() {
                return List.of();
            }
        };
        dev.kage.shop.domain.GoldPort goldPort = new dev.kage.shop.domain.GoldPort() {
            @Override
            public boolean deductGold(dev.kage.shop.domain.Transaction tx, long characterId, long amount) {
                shopDeductCallCount.incrementAndGet();
                long balance = shopGoldBalances.computeIfAbsent(characterId, id -> SHOP_STARTING_GOLD);
                if (balance < amount) {
                    return false;
                }
                shopGoldBalances.put(characterId, balance - amount);
                return true;
            }

            @Override
            public void creditGold(dev.kage.shop.domain.Transaction tx, long characterId, long amount) {
                shopGoldBalances.merge(characterId, amount, Long::sum);
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
                        shopGrantCallCount.incrementAndGet();
                        return true;
                    }

                    @Override
                    public boolean removeQuantity(
                            dev.kage.shop.domain.Transaction tx, long characterId, long itemId, int quantity) {
                        return true;
                    }
                };
        dev.kage.shop.domain.AuditPort shopAuditPort = (tx, characterId, action, detailJson) -> {};
        dev.kage.shop.domain.IdempotencyPort shopIdempotencyPort = (tx, idempotencyKey) -> {
            if (!usedShopIdempotencyKeys.add(idempotencyKey)) {
                throw new dev.kage.shop.domain.IdempotencyKeyReusedException(idempotencyKey);
            }
        };
        dev.kage.shop.application.ShopPorts ports = new dev.kage.shop.application.ShopPorts(
                catalogPort, goldPort, inventoryGatewayPort, shopAuditPort, shopIdempotencyPort);
        dev.kage.shop.application.ListShopItemsUseCase listShopItemsUseCase =
                new dev.kage.shop.application.ListShopItemsUseCase(catalogPort);
        dev.kage.shop.application.BuyItemUseCase buyItemUseCase =
                new dev.kage.shop.application.BuyItemUseCase(ports, action -> action.accept(null));
        dev.kage.shop.application.SellItemUseCase sellItemUseCase =
                new dev.kage.shop.application.SellItemUseCase(ports, action -> action.accept(null));
        return new dev.kage.shop.api.ShopFacade(listShopItemsUseCase, buyItemUseCase, sellItemUseCase);
    }

    // ============================================================================================
    // Fake facade còn lại — KHÔNG dùng trực tiếp ở test nào trong file này (trừ ChatFacade, dùng
    // thật cho rate-limit chat), chỉ cần đủ để GatewayFacades/ContentFacades/SocialFacades compile
    // và start() được — bản sao Y HỆT GatewayServerIntegrationTest (đã verify hoạt động đúng ở đó).
    // ============================================================================================

    private InventoryFacade fakeInventoryFacade() {
        InventoryPort inventoryPort = new InventoryPort() {
            @Override
            public Optional<Inventory> findByCharacter(long characterId) {
                return Optional.of(new Inventory(characterId, 20, List.of()));
            }

            @Override
            public Inventory save(dev.kage.inventory.domain.Transaction tx, Inventory inventory) {
                return inventory;
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

    private dev.kage.dialogue.api.DialogueFacade fakeDialogueFacade() {
        dev.kage.dialogue.domain.NpcTemplatePort npcTemplatePort = npcCode -> Optional.empty();
        dev.kage.dialogue.application.GetDialogueNodeUseCase useCase =
                new dev.kage.dialogue.application.GetDialogueNodeUseCase(npcTemplatePort);
        return new dev.kage.dialogue.api.DialogueFacade(useCase);
    }

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
        dev.kage.friend.application.FriendUseCases useCases = new dev.kage.friend.application.FriendUseCases(
                new dev.kage.friend.application.SendFriendRequestUseCase(friendPort),
                new dev.kage.friend.application.AcceptFriendRequestUseCase(friendPort),
                new dev.kage.friend.application.RejectFriendRequestUseCase(friendPort),
                new dev.kage.friend.application.RemoveFriendUseCase(friendPort),
                new dev.kage.friend.application.ListFriendsUseCase(friendPort),
                new dev.kage.friend.application.ListPendingFriendRequestsUseCase(friendPort));
        return new dev.kage.friend.api.FriendFacade(useCases);
    }

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

    // ChatFacade THẬT (không throw) — cần cho shouldRejectChatCommands_pastRateLimitThreshold...,
    // cùng cách GatewayServerIntegrationTest#fakeChatFacade().
    private dev.kage.chat.api.ChatFacade fakeChatFacade() {
        dev.kage.chat.domain.ChatAuditPort chatAuditPort = (characterId, action, detailJson) -> {};
        return new dev.kage.chat.api.ChatFacade(new dev.kage.chat.application.SendChatMessageUseCase(chatAuditPort));
    }

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

    private dev.kage.party.api.PartyFacade fakePartyFacade() {
        dev.kage.party.domain.PartyPort partyPort = new dev.kage.party.domain.PartyPort() {
            private final Map<Long, dev.kage.party.domain.Party> partiesById =
                    new HashMap<Long, dev.kage.party.domain.Party>();
            private final Map<Long, List<dev.kage.party.domain.PartyMember>> membersByPartyId =
                    new HashMap<Long, List<dev.kage.party.domain.PartyMember>>();
            private final Map<Long, dev.kage.party.domain.PartyInvite> invitesById =
                    new HashMap<Long, dev.kage.party.domain.PartyInvite>();
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
                membersByPartyId.put(partyId, new ArrayList<dev.kage.party.domain.PartyMember>(List.of(leader)));
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
                        .computeIfAbsent(invite.partyId(), id -> new ArrayList<dev.kage.party.domain.PartyMember>())
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
                new dev.kage.party.application.FindMyPartyUseCase(partyPort));
        return new dev.kage.party.api.PartyFacade(useCases);
    }

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

    private dev.kage.pvp.api.PvpFacade fakePvpFacade() {
        dev.kage.pvp.domain.PvpRatingPort ratingPort = new dev.kage.pvp.domain.PvpRatingPort() {
            private final Map<Long, dev.kage.pvp.domain.PvpRating> ratingsByCharacterId =
                    new HashMap<Long, dev.kage.pvp.domain.PvpRating>();

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

    // ============================================================================================
    // Helper socket — Y HỆT pattern GatewayServerIntegrationTest.
    // ============================================================================================

    private static long connectAndCreateCharacterOnly(Socket socket, DataInputStream in, String username)
            throws Exception {
        ConnectRequest connectRequest =
                ConnectRequest.newBuilder().setUsername(username).build();
        sendFrame(socket, Opcode.OPCODE_CONNECT_REQUEST_VALUE, 1, connectRequest.toByteArray());
        ConnectResponse.parseFrom(readFrame(in).payload());

        CreateCharacterRequest createRequest = CreateCharacterRequest.newBuilder()
                .setName("Hero" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        sendFrame(socket, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 2, createRequest.toByteArray());
        CreateCharacterResponse response =
                CreateCharacterResponse.parseFrom(readFrame(in).payload());
        return response.getCharacter().getId();
    }

    private static long connectCreateAndSelect(Socket socket, DataInputStream in, String username) throws Exception {
        long characterId = connectAndCreateCharacterOnly(socket, in, username);
        SelectCharacterRequest selectRequest =
                SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        sendFrame(socket, Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, 3, selectRequest.toByteArray());
        readFrame(in); // SelectCharacterResponse — nội dung đã verify ở GatewayPipelineTest.
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

    private static void sendPing(Socket socket, int sequenceId, long clientTimestampMs) throws Exception {
        PingCommand ping =
                PingCommand.newBuilder().setClientTimestampMs(clientTimestampMs).build();
        sendFrame(socket, Opcode.OPCODE_PING_VALUE, sequenceId, ping.toByteArray());
    }

    /** AoI Event tới bất kỳ lúc nào giữa các Response khác — đọc tới khi thấy đúng opcode cần. */
    private static PacketCodec.Frame readUntilOpcode(DataInputStream in, int expectedOpcode) throws Exception {
        Map<Integer, Integer> seen = new HashMap<Integer, Integer>();
        for (int attempt = 0; attempt < 1000; attempt++) {
            PacketCodec.Frame frame = readFrame(in);
            if (frame.header().opcode() == expectedOpcode) {
                return frame;
            }
            seen.merge(frame.header().opcode(), 1, Integer::sum);
        }
        throw new AssertionError("Không thấy opcode " + expectedOpcode + " sau 1000 frame, đã thấy: " + seen);
    }

    private static PacketCodec.Frame readFrame(DataInputStream in) throws Exception {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return PacketCodec.decodeFrame(bytes);
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
