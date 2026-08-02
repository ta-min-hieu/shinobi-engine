package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.quest.api.QuestFacade;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ProcessMonsterKilledUseCase;
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
        ProcessMonsterKilledUseCase processMonsterKilledUseCase =
                new ProcessMonsterKilledUseCase(progressPort, templatePort, tx);
        dev.kage.quest.domain.IdempotencyPort questIdempotencyPort = (t, idempotencyKey) -> {};
        QuestRewardPorts rewardPorts =
                new QuestRewardPorts(expPort, inventoryGrantPort, auditPort, questIdempotencyPort);
        ClaimQuestRewardUseCase claimQuestRewardUseCase =
                new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, tx);
        return new QuestFacade(acceptQuestUseCase, processMonsterKilledUseCase, claimQuestRewardUseCase);
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
                fakeInventoryFacade(100), fakeQuestFacade(), new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, channel.pipeline().firstContext());

        registry.onMonsterKilled(1L, "training_dummy");

        PacketCodec.Frame frame = awaitOutboundFrame(channel);
        ItemGrantedEvent event = ItemGrantedEvent.parseFrom(frame.payload());
        assertThat(frame.header().opcode()).isEqualTo(Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE);
        assertThat(event.getItemTemplateId()).isEqualTo(100L);
        assertThat(event.getQuantity()).isEqualTo(2);
    }

    @Test
    void shouldSendNothing_whenLootNeverDrops() {
        // 0% -> tất định không rơi gì
        registry = new ConnectedCharacterRegistry(
                fakeInventoryFacade(0), fakeQuestFacade(), new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, channel.pipeline().firstContext());

        registry.onMonsterKilled(1L, "training_dummy");

        sleepQuietly(200); // đủ lâu để lootExecutor (Virtual Thread) chạy xong nếu (sai) có gửi gì
        assertThat(channel.outboundMessages()).isEmpty();
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
        AddItemUseCase addItemUseCase = new AddItemUseCase(inventoryPort, action -> action.accept(null));
        GrantLootUseCase grantLootUseCase =
                new GrantLootUseCase(dropTablePort, inventoryPort, action -> action.accept(null));
        // Equip/unequip không dùng trong test này (chỉ verify grantLoot) — fake tối thiểu để compile.
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
        EnhancePorts enhancePorts =
                new EnhancePorts(inventoryPort, goldPort, auditPort, itemTemplatePort, idempotencyPort);
        EnhanceItemUseCase enhanceItemUseCase = new EnhanceItemUseCase(enhancePorts, action -> action.accept(null));
        return new InventoryFacade(addItemUseCase, grantLootUseCase, equipmentUseCases, enhanceItemUseCase);
    }

    /** onMonsterKilled đẩy việc sang Virtual Thread riêng (lootExecutor) — poll thay vì đọc ngay. */
    private static PacketCodec.Frame awaitOutboundFrame(EmbeddedChannel channel) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(4));
        while (Instant.now().isBefore(deadline)) {
            Object message = channel.readOutbound();
            if (message instanceof PacketCodec.Frame frame) {
                return frame;
            }
            sleepQuietly(10);
        }
        throw new AssertionError("Không nhận được outbound frame nào sau khi chờ");
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
