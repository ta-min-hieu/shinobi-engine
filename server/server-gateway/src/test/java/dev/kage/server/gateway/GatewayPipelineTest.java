package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.junit.jupiter.api.Test;

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
import dev.kage.protocol.SelectFactionRequest;
import dev.kage.protocol.SelectFactionResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Test TOÀN BỘ pipeline (framing + codec + handler) qua {@link EmbeddedChannel} — không cần
 * socket thật (TESTING.md §2: "server-gateway: Netty client giả kết nối thật qua Netty
 * embedded"). Cùng cấu trúc pipeline với {@link GatewayBootstrap} — nếu hai bên lệch nhau, đây
 * là chỗ phải đồng bộ lại.
 */
class GatewayPipelineTest {

    @Test
    void shouldReplyPongToPing_withSameSequenceId() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = newPipeline();
        PingCommand ping = PingCommand.newBuilder().setClientTimestampMs(123L).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, 7);

        writeInboundFrame(channel, header, ping.toByteArray());
        PacketCodec.Frame response = readOutboundFrame(channel);

        // Response PHẢI khớp sequenceId của Command (NETWORK.md §105 request/response correlation).
        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_PONG_VALUE);
        assertThat(response.header().sequenceId()).isEqualTo(7);

        PongResponse pong = PongResponse.parseFrom(response.payload());
        assertThat(pong.getClientTimestampMs()).isEqualTo(123L);
        assertThat(pong.getServerTimestampMs()).isGreaterThan(0);
    }

    @Test
    void shouldReplyConnectResponse_toConnectRequest() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = newPipeline();
        ConnectRequest request =
                ConnectRequest.newBuilder().setUsername("kagehero").build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CONNECT_REQUEST_VALUE, ProtocolVersion.CURRENT, 1);

        writeInboundFrame(channel, header, request.toByteArray());
        PacketCodec.Frame response = readOutboundFrame(channel);

        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_CONNECT_RESPONSE_VALUE);
        ConnectResponse connectResponse = ConnectResponse.parseFrom(response.payload());
        assertThat(connectResponse.getSuccess()).isTrue();
        assertThat(connectResponse.getSessionId()).isNotBlank();
        assertThat(connectResponse.getAccountId()).isPositive();
    }

    @Test
    void shouldReplyError_forUnknownOpcode() {
        EmbeddedChannel channel = newPipeline();
        PacketHeader header = new PacketHeader(9999, ProtocolVersion.CURRENT, 5);

        writeInboundFrame(channel, header, new byte[0]);
        PacketCodec.Frame response = readOutboundFrame(channel);

        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    @Test
    void shouldRejectListCharacters_whenNotConnectedYet() {
        EmbeddedChannel channel = newPipeline();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, ProtocolVersion.CURRENT, 1);

        writeInboundFrame(
                channel, header, ListCharactersRequest.newBuilder().build().toByteArray());
        PacketCodec.Frame response = readOutboundFrame(channel);

        // accountId gắn vào CONNECTION lúc Connect thành công (xem GatewayChannelHandler) — chưa
        // Connect thì chưa có accountId, phải từ chối chứ không đọc packet.
        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    @Test
    void shouldCreateThenListCharacter_afterConnect() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = newPipeline();
        connect(channel, "kagehero2");

        CreateCharacterRequest createRequest = CreateCharacterRequest.newBuilder()
                .setName("KageHero")
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 2),
                createRequest.toByteArray());
        PacketCodec.Frame createResponseFrame = readOutboundFrame(channel);
        assertThat(createResponseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE);
        CreateCharacterResponse createResponse = CreateCharacterResponse.parseFrom(createResponseFrame.payload());
        assertThat(createResponse.getSuccess()).isTrue();
        assertThat(createResponse.getCharacter().getName()).isEqualTo("KageHero");

        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, ProtocolVersion.CURRENT, 3),
                ListCharactersRequest.newBuilder().build().toByteArray());
        PacketCodec.Frame listResponseFrame = readOutboundFrame(channel);
        assertThat(listResponseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE);
        ListCharactersResponse listResponse = ListCharactersResponse.parseFrom(listResponseFrame.payload());
        assertThat(listResponse.getCharactersList())
                .extracting(c -> c.getName())
                .containsExactly("KageHero");
    }

    @Test
    void shouldSelectCharacter_afterCreatingIt() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = newPipeline();
        connect(channel, "selecttest1");
        long characterId = createCharacter(channel, "SelectHero", 2);

        SelectCharacterRequest request =
                SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 3),
                request.toByteArray());
        PacketCodec.Frame responseFrame = readOutboundFrame(channel);

        assertThat(responseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE);
        SelectCharacterResponse response = SelectCharacterResponse.parseFrom(responseFrame.payload());
        assertThat(response.getSuccess()).isTrue();
    }

    @Test
    void shouldRejectSelectCharacter_whenBelongsToDifferentAccount() throws InvalidProtocolBufferException {
        FakeCharacterPort characterPort = new FakeCharacterPort();
        // Nhân vật thuộc account KHÁC — không tạo được qua chính kết nối test này (login luôn tạo
        // đúng account của username đang dùng) — seed thẳng vào Fake Port để mô phỏng đúng kịch
        // bản IDOR mà SECURITY.md §4 mô tả.
        Character otherAccountCharacter =
                characterPort.create(null, new NewCharacter(999L, "OtherHero", Faction.HOA, "seed-idem"));

        EmbeddedChannel channel = newPipeline(characterPort);
        connect(channel, "selecttest2");

        SelectCharacterRequest request = SelectCharacterRequest.newBuilder()
                .setCharacterId(otherAccountCharacter.id())
                .build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 2),
                request.toByteArray());
        PacketCodec.Frame responseFrame = readOutboundFrame(channel);

        assertThat(responseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    @Test
    void shouldSelectFaction_whenCharacterLevelAtLeastTen() throws InvalidProtocolBufferException {
        FakeCharacterPort characterPort = new FakeCharacterPort();
        EmbeddedChannel channel = newPipeline(characterPort);
        connect(channel, "factiontest1");
        long characterId = createCharacter(channel, "FactionHero", 2);
        bumpLevel(characterPort, characterId, 10); // chưa có cơ chế lên cấp giữa phiên — seed thẳng

        selectCharacter(channel, characterId, 3);

        SelectFactionRequest request = SelectFactionRequest.newBuilder()
                .setFaction(dev.kage.protocol.Faction.FACTION_THUY)
                .build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_FACTION_REQUEST_VALUE, ProtocolVersion.CURRENT, 4),
                request.toByteArray());
        PacketCodec.Frame responseFrame = readOutboundFrame(channel);

        assertThat(responseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_SELECT_FACTION_RESPONSE_VALUE);
        SelectFactionResponse response = SelectFactionResponse.parseFrom(responseFrame.payload());
        assertThat(response.getSuccess()).isTrue();
        assertThat(characterPort.findById(characterId).orElseThrow().faction()).isEqualTo(Faction.THUY);
    }

    @Test
    void shouldRejectSelectFaction_whenCharacterLevelBelowTen() throws InvalidProtocolBufferException {
        FakeCharacterPort characterPort = new FakeCharacterPort();
        EmbeddedChannel channel = newPipeline(characterPort);
        connect(channel, "factiontest2");
        long characterId = createCharacter(channel, "LowLevelHero", 2); // level=1 mặc định (CreateCharacterUseCase)
        selectCharacter(channel, characterId, 3);

        SelectFactionRequest request = SelectFactionRequest.newBuilder()
                .setFaction(dev.kage.protocol.Faction.FACTION_THUY)
                .build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_FACTION_REQUEST_VALUE, ProtocolVersion.CURRENT, 4),
                request.toByteArray());
        PacketCodec.Frame responseFrame = readOutboundFrame(channel);

        assertThat(responseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
        assertThat(characterPort.findById(characterId).orElseThrow().faction()).isEqualTo(Faction.HOA); // không đổi
    }

    // ================= ADR-035 (Accepted 2026-08-27) — 4 opcode đồng bộ trạng thái =================

    @Test
    void shouldListInventoryWithEquippedSlots_afterSelectingCharacter() throws InvalidProtocolBufferException {
        InventoryFacade inventoryFacade = fakeInventoryFacade(
                List.of(
                        new dev.kage.inventory.domain.ItemStack(42L, 2L, 1, 3),
                        new dev.kage.inventory.domain.ItemStack(43L, 1L, 5, 0)),
                Map.of(dev.kage.inventory.domain.EquipmentSlotType.WEAPON, 42L),
                List.of());
        EmbeddedChannel channel = newPipeline(new FakeCharacterPort(), inventoryFacade, fakeQuestFacade());
        connect(channel, "invtest1");
        long characterId = createCharacter(channel, "InvHero", 2);
        selectCharacter(channel, characterId, 3);

        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE, ProtocolVersion.CURRENT, 4),
                dev.kage.protocol.ListInventoryRequest.newBuilder().build().toByteArray());
        PacketCodec.Frame frame = readOutboundFrame(channel);

        assertThat(frame.header().opcode()).isEqualTo(Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE);
        assertThat(frame.header().sequenceId()).isEqualTo(4);
        dev.kage.protocol.ListInventoryResponse response =
                dev.kage.protocol.ListInventoryResponse.parseFrom(frame.payload());
        assertThat(response.getCapacity()).isEqualTo(20);
        assertThat(response.getItemsList())
                .extracting(dev.kage.protocol.InventoryItemView::getItemId)
                .containsExactly(42L, 43L);
        // slot_index = vị trí trong danh sách, đúng cách PostgresInventoryAdapter đánh số lúc save.
        assertThat(response.getItems(0).getSlotIndex()).isZero();
        assertThat(response.getItems(1).getSlotIndex()).isEqualTo(1);
        assertThat(response.getItems(0).getEnhanceLevel()).isEqualTo(3);
        // Trang bị đi CÙNG response túi đồ — không có LIST_EQUIPMENT riêng (ADR-035 Decision-1).
        assertThat(response.getEquippedList()).hasSize(1);
        assertThat(response.getEquipped(0).getSlot()).isEqualTo(dev.kage.protocol.EquipmentSlot.EQUIPMENT_SLOT_WEAPON);
        assertThat(response.getEquipped(0).getItemId()).isEqualTo(42L);
    }

    @Test
    void shouldRejectListInventory_whenNoCharacterBoundToSession() {
        EmbeddedChannel channel = newPipeline();
        connect(channel, "invtest2"); // đã đăng nhập nhưng CHƯA chọn nhân vật

        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE, ProtocolVersion.CURRENT, 2),
                dev.kage.protocol.ListInventoryRequest.newBuilder().build().toByteArray());

        // characterId CHỈ đến từ session — packet không có field nào để mượn (SECURITY.md §4).
        assertThat(readOutboundFrame(channel).header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    @Test
    void shouldSendFullItemCatalog_whenClientHasNoCachedVersion() throws InvalidProtocolBufferException {
        InventoryFacade inventoryFacade = fakeInventoryFacade(
                List.of(),
                Map.of(),
                List.of(
                        new dev.kage.inventory.domain.ItemCatalogEntry(
                                1L, "kunai", "item.kunai.name", 99, null, 0, 0, 0, 0, 1),
                        new dev.kage.inventory.domain.ItemCatalogEntry(
                                2L,
                                "wooden_sword",
                                "item.wooden_sword.name",
                                1,
                                dev.kage.inventory.domain.EquipmentSlotType.WEAPON,
                                5,
                                0,
                                0,
                                0,
                                5)));
        EmbeddedChannel channel = newPipeline(new FakeCharacterPort(), inventoryFacade, fakeQuestFacade());
        connect(channel, "cattest1");
        selectCharacter(channel, createCharacter(channel, "CatHero", 2), 3);

        dev.kage.protocol.ItemCatalogResponse response = requestCatalog(channel, "", 4);

        assertThat(response.getUnchanged()).isFalse();
        assertThat(response.getVersion()).isNotBlank();
        assertThat(response.getEntriesList())
                .extracting(dev.kage.protocol.ItemCatalogEntry::getCode)
                .containsExactly("kunai", "wooden_sword");
        // Item không mặc được (equip_slot NULL trong DB) -> UNSPECIFIED, không phải bỏ trống ngầm.
        assertThat(response.getEntries(0).getEquipSlot())
                .isEqualTo(dev.kage.protocol.EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED);
        assertThat(response.getEntries(1).getEquipSlot())
                .isEqualTo(dev.kage.protocol.EquipmentSlot.EQUIPMENT_SLOT_WEAPON);
        assertThat(response.getEntries(1).getBonusAtk()).isEqualTo(5);
        // ADR-036 D5 — 4 field mới (7..10) thật sự đi qua dây, không chỉ tồn tại trong .proto.
        assertThat(response.getEntries(1).getRequiredLevel()).isEqualTo(5);
        assertThat(response.getEntries(0).getRequiredLevel()).isEqualTo(1);
        // KHÔNG có tên hiển thị/icon — chỉ name_key để client tra bundle i18n (ADR-035 Decision-3).
        assertThat(response.getEntries(1).getNameKey()).isEqualTo("item.wooden_sword.name");
    }

    /**
     * Nhánh {@code unchanged=true} chỉ chạy từ lần đăng nhập THỨ HAI trở đi — ADR-035 cảnh báo rõ
     * là nó dễ mục nếu không có test riêng, nên test này CỐ Ý tồn tại dù trông thừa.
     */
    @Test
    void shouldReportUnchanged_whenClientAlreadyHasCurrentCatalogVersion() throws InvalidProtocolBufferException {
        InventoryFacade inventoryFacade = fakeInventoryFacade(
                List.of(),
                Map.of(),
                List.of(new dev.kage.inventory.domain.ItemCatalogEntry(
                        1L, "kunai", "item.kunai.name", 99, null, 0, 0, 0, 0, 1)));
        EmbeddedChannel channel = newPipeline(new FakeCharacterPort(), inventoryFacade, fakeQuestFacade());
        connect(channel, "cattest2");
        selectCharacter(channel, createCharacter(channel, "CatHero2", 2), 3);

        String version = requestCatalog(channel, "", 4).getVersion(); // "lần đăng nhập thứ nhất"
        dev.kage.protocol.ItemCatalogResponse second = requestCatalog(channel, version, 5); // lần thứ hai

        assertThat(second.getUnchanged()).isTrue();
        assertThat(second.getEntriesList()).isEmpty(); // client giữ nguyên cache, không tải lại
        assertThat(second.getVersion()).isEqualTo(version);
    }

    @Test
    void shouldSendFullCatalog_whenClientSendsStaleVersion() throws InvalidProtocolBufferException {
        InventoryFacade inventoryFacade = fakeInventoryFacade(
                List.of(),
                Map.of(),
                List.of(new dev.kage.inventory.domain.ItemCatalogEntry(
                        1L, "kunai", "item.kunai.name", 99, null, 0, 0, 0, 0, 1)));
        EmbeddedChannel channel = newPipeline(new FakeCharacterPort(), inventoryFacade, fakeQuestFacade());
        connect(channel, "cattest3");
        selectCharacter(channel, createCharacter(channel, "CatHero3", 2), 3);

        dev.kage.protocol.ItemCatalogResponse response = requestCatalog(channel, "version-cu-roi", 4);

        // SERVER phán quyết, không phải client: version lạ thì gửi lại TOÀN BỘ (thay hẳn, không cộng dồn).
        assertThat(response.getUnchanged()).isFalse();
        assertThat(response.getEntriesList()).hasSize(1);
    }

    @Test
    void shouldListQuestsIncludingClaimed_soRewardSurvivesRelog() throws InvalidProtocolBufferException {
        dev.kage.quest.api.QuestFacade questFacade = fakeQuestFacade(List.of(
                new dev.kage.quest.domain.QuestLogEntry(
                        "kill_training_dummies", dev.kage.quest.domain.QuestStatus.COMPLETED),
                new dev.kage.quest.domain.QuestLogEntry(
                        "talk_to_town_elder", dev.kage.quest.domain.QuestStatus.CLAIMED)));
        EmbeddedChannel channel = newPipeline(new FakeCharacterPort(), fakeInventoryFacade(), questFacade);
        connect(channel, "questtest1");
        selectCharacter(channel, createCharacter(channel, "QuestHero", 2), 3);

        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_LIST_QUESTS_REQUEST_VALUE, ProtocolVersion.CURRENT, 4),
                dev.kage.protocol.ListQuestsRequest.newBuilder().build().toByteArray());
        PacketCodec.Frame frame = readOutboundFrame(channel);

        assertThat(frame.header().opcode()).isEqualTo(Opcode.OPCODE_LIST_QUESTS_RESPONSE_VALUE);
        dev.kage.protocol.ListQuestsResponse response = dev.kage.protocol.ListQuestsResponse.parseFrom(frame.payload());
        // quest_code sống sót qua relog — đây chính là thứ ClaimQuestRewardCommand bắt buộc phải có.
        assertThat(response.getQuestsList())
                .extracting(dev.kage.protocol.QuestLogEntry::getQuestCode)
                .containsExactly("kill_training_dummies", "talk_to_town_elder");
        assertThat(response.getQuests(0).getStatus()).isEqualTo(dev.kage.protocol.QuestStatus.QUEST_STATUS_COMPLETED);
        // CLAIMED phải có mặt: lọc phía server thì client không phân biệt "chưa nhận" với "đã trả".
        assertThat(response.getQuests(1).getStatus()).isEqualTo(dev.kage.protocol.QuestStatus.QUEST_STATUS_CLAIMED);
    }

    @Test
    void shouldRejectListQuests_whenNoCharacterBoundToSession() {
        EmbeddedChannel channel = newPipeline();
        connect(channel, "questtest2");

        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_LIST_QUESTS_REQUEST_VALUE, ProtocolVersion.CURRENT, 2),
                dev.kage.protocol.ListQuestsRequest.newBuilder().build().toByteArray());

        assertThat(readOutboundFrame(channel).header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    @Test
    void shouldPushGoldBalance_immediatelyAfterEnteringWorld() throws InvalidProtocolBufferException {
        FakeCharacterPort characterPort = new FakeCharacterPort();
        EmbeddedChannel channel = newPipeline(characterPort);
        connect(channel, "goldtest1");
        long characterId = createCharacter(channel, "GoldHero", 2);
        characterPort.goldByCharacterId.put(characterId, 1234L);

        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 3),
                SelectCharacterRequest.newBuilder()
                        .setCharacterId(characterId)
                        .build()
                        .toByteArray());
        List<PacketCodec.Frame> frames = readOutboundFrames(channel);

        // Người chơi phải thấy số vàng TRƯỚC khi bấm mua, thay vì học bằng cách bị INSUFFICIENT_GOLD.
        PacketCodec.Frame goldFrame = frames.stream()
                .filter(candidate -> candidate.header().opcode() == Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không có GoldChangedEvent sau khi vào world"));
        dev.kage.protocol.GoldChangedEvent event = dev.kage.protocol.GoldChangedEvent.parseFrom(goldFrame.payload());
        assertThat(event.getGold()).isEqualTo(1234L);
        assertThat(event.getDelta()).isZero(); // đồng bộ số dư, không phải một lần vàng đổi
        assertThat(event.getReasonKey()).isEqualTo("gold.reason.enter_world");
        // Response của chính lệnh phải đi TRƯỚC event kèm theo (NETWORK.md §5 correlation).
        assertThat(frames.get(0).header().opcode()).isEqualTo(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE);
    }

    private static dev.kage.protocol.ItemCatalogResponse requestCatalog(
            EmbeddedChannel channel, String knownVersion, int sequenceId) throws InvalidProtocolBufferException {
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_GET_ITEM_CATALOG_REQUEST_VALUE, ProtocolVersion.CURRENT, sequenceId),
                dev.kage.protocol.GetItemCatalogRequest.newBuilder()
                        .setKnownVersion(knownVersion)
                        .build()
                        .toByteArray());
        PacketCodec.Frame frame = readOutboundFrame(channel);
        assertThat(frame.header().opcode()).isEqualTo(Opcode.OPCODE_ITEM_CATALOG_RESPONSE_VALUE);
        return dev.kage.protocol.ItemCatalogResponse.parseFrom(frame.payload());
    }

    // Chưa có cơ chế cộng EXP/lên cấp giữa phiên thật (chỉ qua ClaimQuestRewardUseCase, ngoài phạm
    // vi test này) — seed thẳng level vào Fake Port để test được nhánh "đủ Lv10" (SelectFactionUseCase).
    private static void bumpLevel(FakeCharacterPort characterPort, long characterId, int level) {
        Character character = characterPort.findById(characterId).orElseThrow();
        int index = characterPort.characters.indexOf(character);
        characterPort.characters.set(
                index,
                new Character(
                        character.id(),
                        character.accountId(),
                        character.realmId(),
                        character.name(),
                        character.faction(),
                        level,
                        character.exp(),
                        character.rebirthCount(),
                        character.createdAt()));
    }

    private static void selectCharacter(EmbeddedChannel channel, long characterId, int sequenceId)
            throws InvalidProtocolBufferException {
        SelectCharacterRequest request =
                SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, sequenceId),
                request.toByteArray());
        SelectCharacterResponse response =
                SelectCharacterResponse.parseFrom(readOutboundFrame(channel).payload());
        assertThat(response.getSuccess())
                .as("setup: select character phải thành công trước khi test")
                .isTrue();
    }

    private static void connect(EmbeddedChannel channel, String username) {
        ConnectRequest request =
                ConnectRequest.newBuilder().setUsername(username).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CONNECT_REQUEST_VALUE, ProtocolVersion.CURRENT, 1);
        writeInboundFrame(channel, header, request.toByteArray());
        readOutboundFrame(channel); // tiêu thụ ConnectResponse — caller không kiểm tra lại nội dung
    }

    private static long createCharacter(EmbeddedChannel channel, String name, int sequenceId)
            throws InvalidProtocolBufferException {
        CreateCharacterRequest request = CreateCharacterRequest.newBuilder()
                .setName(name)
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, sequenceId);
        writeInboundFrame(channel, header, request.toByteArray());
        CreateCharacterResponse response =
                CreateCharacterResponse.parseFrom(readOutboundFrame(channel).payload());
        return response.getCharacter().getId();
    }

    private static EmbeddedChannel newPipeline() {
        return newPipeline(new FakeCharacterPort());
    }

    // WorldFacade không start() ở test này (xem newPipeline) nên onMonsterKilled không bao giờ
    // thật sự bị gọi — Fake rỗng chỉ để thoả constructor, không kiểm tra hành vi ở đây.
    private static InventoryFacade fakeInventoryFacade() {
        return fakeInventoryFacade(List.of(), Map.of(), List.of());
    }

    /**
     * Bản seed sẵn dữ liệu — dùng cho test LIST_INVENTORY/GET_ITEM_CATALOG (ADR-035). Overload
     * không tham số ở trên giữ nguyên cho ~15 test cũ vốn không quan tâm túi đồ.
     */
    private static InventoryFacade fakeInventoryFacade(
            List<dev.kage.inventory.domain.ItemStack> seededItems,
            Map<dev.kage.inventory.domain.EquipmentSlotType, Long> seededEquipment,
            List<dev.kage.inventory.domain.ItemCatalogEntry> seededCatalog) {
        InventoryPort inventoryPort = new InventoryPort() {
            @Override
            public Optional<Inventory> findByCharacter(long characterId) {
                return Optional.of(new Inventory(characterId, 20, seededItems));
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

            @Override
            public List<dev.kage.inventory.domain.ItemCatalogEntry> listCatalog() {
                return List.copyOf(seededCatalog); // SpotBugs EI: Fake không được lộ list gốc ra ngoài
            }
        };
        EquipmentPort equipmentPort = new EquipmentPort() {
            @Override
            public Optional<Equipment> findByCharacter(long characterId) {
                return Optional.of(new Equipment(characterId, seededEquipment));
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
        EnhancePorts enhancePorts =
                new EnhancePorts(inventoryPort, goldPort, auditPort, itemTemplatePort, idempotencyPort);
        EnhanceItemUseCase enhanceItemUseCase = new EnhanceItemUseCase(enhancePorts, action -> action.accept(null));
        return new InventoryFacade(
                new dev.kage.inventory.api.ItemAcquisitionUseCases(addItemUseCase, grantLootUseCase),
                equipmentUseCases,
                enhanceItemUseCase,
                new dev.kage.inventory.api.InventoryQueryUseCases(
                        new dev.kage.inventory.application.ListInventoryUseCase(inventoryPort, equipmentPort),
                        new dev.kage.inventory.application.GetItemCatalogUseCase(itemTemplatePort)));
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

    // Dialogue không dùng trong test này — fake tối thiểu để compile (cùng convention fakeShopFacade).
    private static dev.kage.dialogue.api.DialogueFacade fakeDialogueFacade() {
        dev.kage.dialogue.domain.NpcTemplatePort npcTemplatePort = npcCode -> Optional.empty();
        dev.kage.dialogue.application.GetDialogueNodeUseCase useCase =
                new dev.kage.dialogue.application.GetDialogueNodeUseCase(npcTemplatePort);
        return new dev.kage.dialogue.api.DialogueFacade(useCase);
    }

    // Quest không dùng trong test này — fake tối thiểu để compile (cùng convention fakeDialogueFacade).
    private static dev.kage.quest.api.QuestFacade fakeQuestFacade() {
        return fakeQuestFacade(List.of());
    }

    /** Bản seed sẵn nhật ký — dùng cho test LIST_QUESTS (ADR-035 Decision-4). */
    private static dev.kage.quest.api.QuestFacade fakeQuestFacade(List<dev.kage.quest.domain.QuestLogEntry> seededLog) {
        dev.kage.quest.domain.QuestProgressPort progressPort = new dev.kage.quest.domain.QuestProgressPort() {
            @Override
            public Optional<dev.kage.quest.domain.QuestProgress> find(long characterId, long questTemplateId) {
                return Optional.empty();
            }

            @Override
            public void save(dev.kage.quest.domain.Transaction tx, dev.kage.quest.domain.QuestProgress progress) {}

            @Override
            public List<dev.kage.quest.domain.QuestLogEntry> listLog(long characterId) {
                return List.copyOf(seededLog); // SpotBugs EI: cùng lý do listCatalog() ở fakeInventoryFacade
            }
        };
        dev.kage.quest.domain.QuestTemplatePort templatePort = questCode -> Optional.empty();
        dev.kage.quest.domain.ExpPort expPort = (tx, characterId, amount) -> {};
        dev.kage.quest.domain.QuestInventoryGrantPort inventoryGrantPort =
                (tx, characterId, itemTemplateCode, quantity) -> true;
        dev.kage.quest.domain.AuditPort auditPort = (tx, characterId, action, detailJson) -> {};
        dev.kage.quest.domain.TransactionRunner tx = action -> action.accept(null);

        dev.kage.quest.application.AcceptQuestUseCase acceptQuestUseCase =
                new dev.kage.quest.application.AcceptQuestUseCase(progressPort, templatePort, tx);
        dev.kage.quest.application.ProcessMonsterKilledUseCase processMonsterKilledUseCase =
                new dev.kage.quest.application.ProcessMonsterKilledUseCase(progressPort, templatePort, tx);
        dev.kage.quest.domain.IdempotencyPort questIdempotencyPort = (t, idempotencyKey) -> {};
        dev.kage.quest.application.QuestRewardPorts rewardPorts = new dev.kage.quest.application.QuestRewardPorts(
                expPort, inventoryGrantPort, auditPort, questIdempotencyPort);
        dev.kage.quest.application.ClaimQuestRewardUseCase claimQuestRewardUseCase =
                new dev.kage.quest.application.ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, tx);
        return new dev.kage.quest.api.QuestFacade(
                acceptQuestUseCase,
                processMonsterKilledUseCase,
                claimQuestRewardUseCase,
                new dev.kage.quest.application.ListQuestsUseCase(progressPort));
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
    private static dev.kage.chat.api.ChatFacade fakeChatFacade() {
        dev.kage.chat.domain.ChatAuditPort chatAuditPort = (characterId, action, detailJson) -> {};
        return new dev.kage.chat.api.ChatFacade(new dev.kage.chat.application.SendChatMessageUseCase(chatAuditPort));
    }

    // Guild không dùng trong test này — fake tối thiểu để compile (cùng convention fakeFriendFacade).
    private static dev.kage.guild.api.GuildFacade fakeGuildFacade() {
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
            public java.util.List<dev.kage.guild.domain.GuildMember> listMembers(long guildId) {
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
            public java.util.List<dev.kage.guild.domain.GuildJoinRequest> listPendingRequests(long guildId) {
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
    private static dev.kage.party.api.PartyFacade fakePartyFacade() {
        dev.kage.party.domain.PartyPort partyPort = new dev.kage.party.domain.PartyPort() {
            @Override
            public Optional<dev.kage.party.domain.PartyMember> findMembershipOf(long characterId) {
                return Optional.empty();
            }

            @Override
            public java.util.List<dev.kage.party.domain.PartyMember> listMembers(long partyId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public dev.kage.party.domain.Party createParty(long leaderCharacterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public Optional<dev.kage.party.domain.PartyInvite> findPendingInviteOfInvitee(long characterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public java.util.List<dev.kage.party.domain.PartyInvite> listPendingInvitesOfInvitee(long characterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public dev.kage.party.domain.PartyInvite invite(long partyId, long inviteeCharacterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public AcceptResult acceptInvite(long inviteId, long characterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean rejectInvite(long inviteId, long characterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public LeaveResult leaveParty(long characterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
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
    private static dev.kage.trade.api.TradeFacade fakeTradeFacade() {
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
                    java.util.List<dev.kage.trade.domain.OfferedItemInput> items) {
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

    // Pvp không dùng trong test này — fake tối thiểu để compile (cùng convention fakeTradeFacade).
    private static dev.kage.pvp.api.PvpFacade fakePvpFacade() {
        dev.kage.pvp.domain.PvpRatingPort ratingPort = new dev.kage.pvp.domain.PvpRatingPort() {
            @Override
            public Optional<dev.kage.pvp.domain.PvpRating> findByCharacterId(long characterId) {
                return Optional.empty();
            }

            @Override
            public void recordMatchResult(
                    long winnerCharacterId, int winnerNewRating, long loserCharacterId, int loserNewRating) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }
        };
        return new dev.kage.pvp.api.PvpFacade(ratingPort);
    }

    private static EmbeddedChannel newPipeline(FakeCharacterPort characterPort) {
        return newPipeline(characterPort, fakeInventoryFacade(), fakeQuestFacade());
    }

    /**
     * Bản nhận sẵn 2 Facade đã seed dữ liệu — dùng cho test ADR-035 (LIST_INVENTORY /
     * GET_ITEM_CATALOG / LIST_QUESTS cần Fake trả về thứ gì đó, không phải rỗng).
     */
    private static EmbeddedChannel newPipeline(
            FakeCharacterPort characterPort,
            InventoryFacade seededInventoryFacade,
            dev.kage.quest.api.QuestFacade seededQuestFacade) {
        // ⚠️ Phải khớp CHÍNH XÁC pipeline thật trong GatewayBootstrap — lệch ở đây là test giả.
        // CỐ Ý bỏ qua SslHandler (ADR-027) — EmbeddedChannel không mô phỏng handshake TLS thật
        // tiện lợi, và đây là test framing/handler logic ở tầng plaintext. TLS thật (handshake,
        // từ chối kết nối không mã hoá) verify ở GatewayServerTlsIntegrationTest (socket thật).
        // AccountFacade/CharacterFacade THẬT (không mock), chỉ Port bên dưới là Fake (TESTING.md
        // §4) — cùng kiểu Fake với *UseCaseTest ở feature-account/feature-character. Executor
        // chạy ĐỒNG BỘ (Runnable::run) — handler thật đẩy việc chạm DB sang authExecutor (Virtual
        // Thread ở production, SERVER.md §2); test cần đồng bộ để readOutboundFrame không bị race.
        // characterPort truyền vào từ ngoài để test seed sẵn dữ liệu (VD nhân vật account khác,
        // xem shouldRejectSelectCharacter_whenBelongsToDifferentAccount).
        LoginOrCreateAccountUseCase loginUseCase = new LoginOrCreateAccountUseCase(
                new FakeAccountPort(), new FakeSessionPort(), action -> action.accept(null));
        AccountFacade accountFacade = new AccountFacade(loginUseCase);

        CreateCharacterUseCase createCharacterUseCase =
                new CreateCharacterUseCase(characterPort, action -> action.accept(null));
        ListCharactersUseCase listCharactersUseCase = new ListCharactersUseCase(characterPort);
        SelectCharacterUseCase selectCharacterUseCase = new SelectCharacterUseCase(characterPort);
        SelectFactionUseCase selectFactionUseCase = new SelectFactionUseCase(characterPort);
        CharacterUseCases useCases = new CharacterUseCases(
                createCharacterUseCase,
                listCharactersUseCase,
                selectCharacterUseCase,
                selectFactionUseCase,
                new GrantExperienceUseCase(characterPort));
        CharacterFacade characterFacade = new CharacterFacade(useCases, characterPort);

        // WorldFacade KHÔNG start() ở đây — test này không verify movement/AoI, chỉ cần
        // attachCharacter() (gọi trong handleSelectCharacterBlocking) không ném lỗi khi enqueue
        // vào hàng đợi chưa có ai drain (không tạo/rò rỉ thread tick cho mỗi test). Tương tự,
        // positionPersister không start() — save() (nếu bị gọi) chạy trực tiếp, không cần scheduler.
        InventoryFacade inventoryFacade = seededInventoryFacade;
        dev.kage.shop.api.ShopFacade shopFacade = fakeShopFacade();
        dev.kage.dialogue.api.DialogueFacade dialogueFacade = fakeDialogueFacade();
        dev.kage.quest.api.QuestFacade questFacade = seededQuestFacade;
        dev.kage.friend.api.FriendFacade friendFacade = fakeFriendFacade();
        dev.kage.mail.api.MailFacade mailFacade = fakeMailFacade();
        dev.kage.chat.api.ChatFacade chatFacade = fakeChatFacade();
        dev.kage.guild.api.GuildFacade guildFacade = fakeGuildFacade();
        dev.kage.party.api.PartyFacade partyFacade = fakePartyFacade();
        dev.kage.trade.api.TradeFacade tradeFacade = fakeTradeFacade();
        dev.kage.pvp.api.PvpFacade pvpFacade = fakePvpFacade();
        io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        GatewayNetworkMetrics networkMetrics = new GatewayNetworkMetrics(meterRegistry);
        GatewayObservability observability = new GatewayObservability(networkMetrics, GatewayTracing.disabled());
        ConnectedCharacterRegistry characterRegistry = new ConnectedCharacterRegistry(
                inventoryFacade, questFacade, characterFacade::grantExperience, networkMetrics);
        WorldFacade firstChannelFacade = new WorldFacade(characterRegistry);
        MainWorldChannelRegistry mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry, firstChannelFacade, meterRegistry, WorldContentCatalog.EMPTY);
        CharacterPositionPersister positionPersister =
                new CharacterPositionPersister(characterFacade, characterRegistry, Duration.ofMinutes(5));
        DungeonInstanceRegistry dungeonInstanceRegistry =
                new DungeonInstanceRegistry(characterRegistry, characterFacade, partyFacade, mainWorldChannels);
        ArenaInstanceRegistry arenaInstanceRegistry =
                new ArenaInstanceRegistry(characterRegistry, characterFacade, pvpFacade, mainWorldChannels);
        WorldGateway worldGateway = new WorldGateway(
                mainWorldChannels,
                characterRegistry,
                positionPersister,
                dungeonInstanceRegistry,
                arenaInstanceRegistry);

        return new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4),
                new LengthFieldPrepender(4),
                new PacketFrameDecoder(),
                new PacketFrameEncoder(),
                new GatewayChannelHandler(
                        new GatewayFacades(
                                accountFacade,
                                characterFacade,
                                inventoryFacade,
                                new ContentFacades(
                                        shopFacade,
                                        dialogueFacade,
                                        questFacade,
                                        new SocialFacades(
                                                friendFacade,
                                                mailFacade,
                                                chatFacade,
                                                guildFacade,
                                                partyFacade,
                                                tradeFacade,
                                                pvpFacade))),
                        worldGateway,
                        Runnable::run,
                        observability));
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

        // ADR-035: gold trong Fake là MỘT ô nhớ chung, đủ cho test "vào world thì được báo số dư"
        // (test tự seed qua goldByCharacterId trước khi selectCharacter).
        final Map<Long, Long> goldByCharacterId = new HashMap<Long, Long>();

        @Override
        public long loadGold(long characterId) {
            return goldByCharacterId.getOrDefault(characterId, 0L);
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

        @Override
        public long addExperience(long characterId, long amount) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public void saveLevel(long characterId, int level) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public void updateFaction(long characterId, Faction faction) {
            for (int i = 0; i < characters.size(); i++) {
                Character c = characters.get(i);
                if (c.id() == characterId) {
                    characters.set(
                            i,
                            new Character(
                                    c.id(),
                                    c.accountId(),
                                    c.realmId(),
                                    c.name(),
                                    faction,
                                    c.level(),
                                    c.exp(),
                                    c.rebirthCount(),
                                    c.createdAt()));
                    return;
                }
            }
        }
    }

    private static void writeInboundFrame(EmbeddedChannel channel, PacketHeader header, byte[] payload) {
        byte[] frameBytes = PacketCodec.encodeFrame(header, payload);
        ByteBuf buf = Unpooled.buffer(4 + frameBytes.length);
        buf.writeInt(frameBytes.length);
        buf.writeBytes(frameBytes);
        channel.writeInbound(buf);
    }

    /**
     * MỌI frame server đã ghi ra, không chỉ frame đầu — {@link #readOutboundFrame} cố ý bỏ phần
     * sau (đủ cho test request/response), nhưng ADR-035 cần thấy EVENT bắn KÈM sau response (VD
     * GoldChangedEvent ngay sau SelectCharacterResponse).
     */
    private static List<PacketCodec.Frame> readOutboundFrames(EmbeddedChannel channel) {
        ByteBuf combined = Unpooled.buffer();
        ByteBuf part;
        while ((part = channel.readOutbound()) != null) {
            combined.writeBytes(part);
            part.release();
        }
        List<PacketCodec.Frame> frames = new ArrayList<PacketCodec.Frame>();
        while (combined.readableBytes() >= 4) {
            int length = combined.readInt();
            byte[] bytes = new byte[length];
            combined.readBytes(bytes);
            frames.add(PacketCodec.decodeFrame(bytes));
        }
        return frames;
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
