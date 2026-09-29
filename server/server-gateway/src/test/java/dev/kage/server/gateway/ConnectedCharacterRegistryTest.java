package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.domain.ExperienceProgress;
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
import dev.kage.inventory.domain.Transaction;
import dev.kage.protocol.ExperienceChangedEvent;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.quest.api.QuestFacade;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ProcessQuestEventUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.quest.domain.ExpPort;
import dev.kage.quest.domain.QuestInventoryGrantPort;
import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestTemplatePort;

/**
 * Verify {@code onMonsterKilled} -> roll drop table -> cộng túi đồ -> gửi {@code
 * ItemGrantedEvent} thật (GAMEPLAY.md §5) — ĐÂY là phần logic MỚI của server-gateway ở lát cắt
 * "rơi đồ", chưa được {@code WorldFacadeTest} (chỉ verify server-world báo đúng người hạ gục) hay
 * {@code PostgresInventoryAdapterIntegrationTest} (chỉ verify DB) phủ tới.
 */
@Timeout(5)
class ConnectedCharacterRegistryTest {

    private ConnectedCharacterRegistry registry;

    // Quest không dùng trong test này (chỉ verify grantLoot) — fake tối thiểu để compile.
    private static QuestFacade fakeQuestFacade() {
        QuestProgressPort progressPort = new QuestProgressPort() {
            @Override
            public Optional<QuestProgress> find(long characterId, long questTemplateId) {
                return Optional.empty();
            }

            @Override
            public void save(dev.kage.quest.domain.Transaction tx, QuestProgress progress) {}
        };
        QuestTemplatePort templatePort = questCode -> Optional.empty();
        ExpPort expPort = (tx, characterId, amount) -> {};
        QuestInventoryGrantPort inventoryGrantPort = (tx, characterId, itemTemplateCode, quantity) -> true;
        dev.kage.quest.domain.AuditPort auditPort = (tx, characterId, action, detailJson) -> {};
        dev.kage.quest.domain.TransactionRunner tx = action -> action.accept(null);

        AcceptQuestUseCase acceptQuestUseCase = new AcceptQuestUseCase(progressPort, templatePort, tx);
        ProcessQuestEventUseCase processQuestEventUseCase = new ProcessQuestEventUseCase(
                progressPort, (t, characterId, itemTemplateCode, quantity) -> false, auditPort, tx);
        dev.kage.quest.domain.IdempotencyPort questIdempotencyPort = (t, idempotencyKey) -> {};
        QuestRewardPorts rewardPorts =
                new QuestRewardPorts(expPort, inventoryGrantPort, auditPort, questIdempotencyPort);
        ClaimQuestRewardUseCase claimQuestRewardUseCase =
                new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, tx);
        return new QuestFacade(
                acceptQuestUseCase,
                processQuestEventUseCase,
                claimQuestRewardUseCase,
                new dev.kage.quest.application.ListQuestsUseCase(progressPort));
    }

    @AfterEach
    void stopRegistry() {
        if (registry != null) {
            registry.stop();
        }
    }

    @Test
    void shouldSendItemGrantedEvent_whenLootRolledSuccessfully() throws InvalidProtocolBufferException {
        // 100% -> tất định, không flaky
        registry = new ConnectedCharacterRegistry(
                fakeInventoryFacade(100),
                fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, channel.pipeline().firstContext());

        // Quái KHÔNG có thưởng EXP (MonsterExperience) — cố ý: EmbeddedChannel không chịu nổi 2
        // Virtual Thread cùng writeAndFlush (EmbeddedEventLoop không thread-safe, gói bị nuốt mất).
        // Đó là giới hạn của bộ khung test, không phải của server thật — Netty channel thật cho
        // phép ghi từ thread bất kỳ. Test EXP nằm riêng bên dưới, một luồng ghi một lúc.
        registry.onMonsterKilled(1L, "quai_khong_co_exp", 0L);

        PacketCodec.Frame frame = awaitOutboundFrame(channel, Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE);
        ItemGrantedEvent event = ItemGrantedEvent.parseFrom(frame.payload());
        assertThat(frame.header().opcode()).isEqualTo(Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE);
        assertThat(event.getItemTemplateId()).isEqualTo(100L);
        assertThat(event.getQuantity()).isEqualTo(2);
    }

    @Test
    void shouldSendNoItemEvent_whenLootNeverDrops() {
        // 0% -> tất định không rơi gì
        registry = new ConnectedCharacterRegistry(
                fakeInventoryFacade(0),
                fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, channel.pipeline().firstContext());

        registry.onMonsterKilled(1L, "quai_khong_co_exp", 0L); // xem test trên về lý do không dùng quái thật

        sleepQuietly(200); // đủ lâu để lootExecutor (Virtual Thread) chạy xong nếu (sai) có gửi gì
        // KHÔNG assert "không có frame nào": từ 2026-08-09 giết quái LUÔN phát
        // ExperienceChangedEvent (opcode 208) dù có rơi đồ hay không — đó là hành vi đúng, test này
        // chỉ nói về LOOT nên phải lọc đúng loại gói thay vì đòi im lặng tuyệt đối.
        assertThat(outboundOpcodes(channel)).doesNotContain(Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE);
    }

    /**
     * Giết quái phải cộng EXP và báo vạch mới (2026-08-09) — trước đó giết quái KHÔNG cho EXP gì.
     * Dùng {@code dropChancePercent=0} để chỉ có ĐÚNG một luồng ghi ra kênh (xem test loot ở trên).
     */
    @Test
    void shouldSendExperienceChangedEvent_whenMonsterKilled() throws InvalidProtocolBufferException {
        registry = new ConnectedCharacterRegistry(
                fakeInventoryFacade(0),
                fakeQuestFacade(),
                (characterId, amount) ->
                        new GrantExperienceUseCase.Result(new ExperienceProgress(3, 500L, 40L, 300L), true),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, channel.pipeline().firstContext());

        // EXP > 0 mới phát sự kiện — 0 là "quái chưa khai EXP", nhánh bỏ qua sớm (xem
        // grantExperienceBlocking). Từ ADR-037 con số này do world chở tới, không tra bảng nữa.
        registry.onMonsterKilled(1L, "training_dummy", 20L);

        PacketCodec.Frame frame = awaitOutboundFrame(channel, Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE);
        ExperienceChangedEvent event = ExperienceChangedEvent.parseFrom(frame.payload());
        assertThat(event.getLevel()).isEqualTo(3);
        assertThat(event.getExpIntoLevel()).isEqualTo(40L);
        assertThat(event.getExpForNextLevel()).isEqualTo(300L);
        assertThat(event.getLeveledUp()).isTrue();
    }

    /** Quái chưa khai báo EXP không được phát gói EXP nào — bỏ qua sớm, không đụng cổng EXP. */
    @Test
    void shouldSendNoExperienceEvent_whenMonsterHasNoReward() {
        registry = new ConnectedCharacterRegistry(
                fakeInventoryFacade(0),
                fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, channel.pipeline().firstContext());

        registry.onMonsterKilled(1L, "quai_khong_co_exp", 0L);

        sleepQuietly(200);
        assertThat(outboundOpcodes(channel)).doesNotContain(Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE);
    }

    /**
     * Đầu ra của điểm phát duy nhất (ADR-035 Decision-3): {@code GoldLedger} ở server-database báo
     * "vàng vừa đổi", {@code GatewayBootstrap} nối thẳng vào method này. Test ở đây kiểm ĐÚNG mắt
     * xích cuối — biến số dư thành packet gửi cho đúng người.
     */
    @Test
    void shouldSendGoldChangedEvent_toTheOwningCharacter() throws InvalidProtocolBufferException {
        registry = new ConnectedCharacterRegistry(
                fakeInventoryFacade(0),
                fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, channel.pipeline().firstContext());

        registry.sendGoldChanged(1L, 90L, -10L, "gold.reason.buy_item");

        PacketCodec.Frame frame = awaitOutboundFrame(channel, Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE);
        dev.kage.protocol.GoldChangedEvent event = dev.kage.protocol.GoldChangedEvent.parseFrom(frame.payload());
        assertThat(event.getGold()).isEqualTo(90L);
        assertThat(event.getDelta()).isEqualTo(-10L);
        assertThat(event.getReasonKey()).isEqualTo("gold.reason.buy_item");
    }

    /**
     * Người chơi ngắt kết nối giữa lúc giao dịch commit là chuyện bình thường — vàng ĐÃ vào DB
     * thật, chỉ mất thông báo. Ném ở đây sẽ làm {@code GoldLedger} tưởng giao dịch hỏng.
     */
    @Test
    void shouldIgnoreGoldChange_whenCharacterAlreadyDisconnected() {
        registry = new ConnectedCharacterRegistry(
                fakeInventoryFacade(0),
                fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, channel.pipeline().firstContext());
        registry.unregister(1L);

        registry.sendGoldChanged(1L, 90L, -10L, "gold.reason.buy_item"); // không được ném

        assertThat(outboundOpcodes(channel)).doesNotContain(Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE);
    }

    /**
     * Đổi hệ phái giữa phiên chơi (opcode 206) phải đổi luôn ngoại hình mà registry đang giữ.
     *
     * <p>Trước bản vá 2026-08-29, {@code appearanceByCharacterId} chỉ được ghi một lần lúc
     * {@code register()}, nên MỌI {@code CharacterSpawnedEvent} phát sau khi đổi hệ phái vẫn mang hệ
     * CŨ — kể cả cho người chơi vừa đi tới lần đầu — cho tới khi nhân vật đó đăng nhập lại.
     */
    @Test
    void shouldSpawnWithTheNewFaction_afterAppearanceUpdated() throws InvalidProtocolBufferException {
        registry = new ConnectedCharacterRegistry(
                fakeInventoryFacade(0),
                fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel observer = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, observer.pipeline().firstContext());
        EmbeddedChannel changed = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(
                2L, changed.pipeline().firstContext(), CharacterAppearance.of(dev.kage.protocol.Faction.FACTION_HOA));

        registry.updateAppearance(2L, CharacterAppearance.of(dev.kage.protocol.Faction.FACTION_THUY));
        registry.onCharacterSpawned(1L, 2L, 3f, 4f);

        PacketCodec.Frame frame = awaitOutboundFrame(observer, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE);
        dev.kage.protocol.CharacterSpawnedEvent event =
                dev.kage.protocol.CharacterSpawnedEvent.parseFrom(frame.payload());
        assertThat(event.getFaction()).isEqualTo(dev.kage.protocol.Faction.FACTION_THUY);
    }

    /**
     * Nhân vật đã ngắt kết nối thì cập nhật ngoại hình phải KHÔNG tạo lại bản ghi — nếu không,
     * {@code appearanceByCharacterId} rò rỉ một dòng cho mỗi lần đổi hệ phái của người đã thoát, và
     * lần đăng nhập sau {@code register()} mới là nguồn chân lý.
     */
    @Test
    void shouldNotResurrectAppearance_whenCharacterAlreadyDisconnected() throws InvalidProtocolBufferException {
        registry = new ConnectedCharacterRegistry(
                fakeInventoryFacade(0),
                fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel observer = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, observer.pipeline().firstContext());

        registry.updateAppearance(2L, CharacterAppearance.of(dev.kage.protocol.Faction.FACTION_THUY));
        registry.onCharacterSpawned(1L, 2L, 3f, 4f);

        PacketCodec.Frame frame = awaitOutboundFrame(observer, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE);
        dev.kage.protocol.CharacterSpawnedEvent event =
                dev.kage.protocol.CharacterSpawnedEvent.parseFrom(frame.payload());
        assertThat(event.getFaction()).isEqualTo(dev.kage.protocol.Faction.FACTION_UNSPECIFIED);
    }

    /** ADR-045 — người quan sát nhận đúng hệ phái MỚI, không phải hệ phái lúc họ nhìn thấy lần đầu. */
    @Test
    void shouldSendNewFactionToObserver_whenAppearanceChanged() throws InvalidProtocolBufferException {
        registry = new ConnectedCharacterRegistry(
                fakeInventoryFacade(0),
                fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel observer = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, observer.pipeline().firstContext());
        EmbeddedChannel changed = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(
                2L, changed.pipeline().firstContext(), CharacterAppearance.of(dev.kage.protocol.Faction.FACTION_HOA));

        registry.updateAppearance(2L, CharacterAppearance.of(dev.kage.protocol.Faction.FACTION_THUY));
        registry.onCharacterAppearanceChanged(1L, 2L);

        PacketCodec.Frame frame = awaitOutboundFrame(observer, Opcode.OPCODE_CHARACTER_APPEARANCE_CHANGED_EVENT_VALUE);
        dev.kage.protocol.CharacterAppearanceChangedEvent event =
                dev.kage.protocol.CharacterAppearanceChangedEvent.parseFrom(frame.payload());
        assertThat(event.getCharacterId()).isEqualTo(2L);
        assertThat(event.getFaction()).isEqualTo(dev.kage.protocol.Faction.FACTION_THUY);
    }

    /**
     * Không tra được ngoại hình (nhân vật vừa ngắt kết nối) thì KHÔNG gửi gì — khác
     * {@code onCharacterSpawned}, nơi {@code unknown()} là câu trả lời đúng vì client vẫn cần một
     * model để vẽ. Ở đây gửi {@code unknown} sẽ thay model ĐANG ĐÚNG bằng model thay thế.
     */
    @Test
    void shouldSendNothing_whenAppearanceOfChangedCharacterIsUnknown() {
        registry = new ConnectedCharacterRegistry(
                fakeInventoryFacade(0),
                fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel observer = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, observer.pipeline().firstContext());

        registry.onCharacterAppearanceChanged(1L, 2L);

        assertThat(outboundOpcodes(observer)).doesNotContain(Opcode.OPCODE_CHARACTER_APPEARANCE_CHANGED_EVENT_VALUE);
    }

    /** Opcode của mọi frame đã ghi ra kênh — để assert theo LOẠI gói thay vì "có/không có gì". */
    private static List<Integer> outboundOpcodes(EmbeddedChannel channel) {
        List<Integer> opcodes = new ArrayList<>();
        for (Object message : channel.outboundMessages()) {
            if (message instanceof PacketCodec.Frame frame) {
                opcodes.add(frame.header().opcode());
            }
        }
        return opcodes;
    }

    private static InventoryFacade fakeInventoryFacade(int dropChancePercent) {
        InventoryPort inventoryPort = new InventoryPort() {
            @Override
            public Optional<Inventory> findByCharacter(long characterId) {
                return Optional.of(new Inventory(characterId, 20, List.of()));
            }

            @Override
            public Inventory save(dev.kage.inventory.domain.Transaction tx, Inventory inventory) {
                // không cần verify save() ở test này — WorldFacadeTest/PostgresInventoryAdapter
                // IntegrationTest đã phủ persistence, test này chỉ verify event gửi đúng.
                return inventory;
            }
        };
        DropTablePort dropTablePort = dropTableCode -> List.of(new DropTableEntry(100L, dropChancePercent, 2, 2));
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
        // Equip/unequip không dùng trong test này (chỉ verify grantLoot) — fake tối thiểu để compile.
        EquipmentPort equipmentPort = new EquipmentPort() {
            @Override
            public Optional<Equipment> findByCharacter(long characterId) {
                return Optional.of(new Equipment(characterId, Map.of()));
            }

            @Override
            public void save(Transaction tx, Equipment equipment) {}
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

    /**
     * onMonsterKilled đẩy việc sang Virtual Thread riêng (lootExecutor) — poll thay vì đọc ngay.
     *
     * <p>Phải chờ ĐÚNG opcode chứ không phải frame đầu tiên: từ 2026-08-09 một cú giết quái sinh ra
     * NHIỀU loại gói (rơi đồ, tiến độ quest, kinh nghiệm) từ các Virtual Thread chạy song song, thứ
     * tự tới không xác định — lấy frame đầu tiên là test flaky.
     */
    private static PacketCodec.Frame awaitOutboundFrame(EmbeddedChannel channel, int expectedOpcode) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(4));
        while (Instant.now().isBefore(deadline)) {
            Object message = channel.readOutbound();
            if (message instanceof PacketCodec.Frame frame && frame.header().opcode() == expectedOpcode) {
                return frame;
            }
            if (message == null) {
                sleepQuietly(10);
            }
        }
        throw new AssertionError("Không nhận được outbound frame opcode=" + expectedOpcode + " sau khi chờ");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted khi chờ lootExecutor chạy thật", e);
        }
    }
}
