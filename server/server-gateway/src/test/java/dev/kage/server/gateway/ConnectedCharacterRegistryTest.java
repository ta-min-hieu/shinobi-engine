package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.inventory.api.EquipmentUseCases;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EnhanceItemUseCase;
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
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verify {@code onMonsterKilled} -> roll drop table -> cộng túi đồ -> gửi {@code
 * ItemGrantedEvent} thật (GAMEPLAY.md §5) — ĐÂY là phần logic MỚI của server-gateway ở lát cắt
 * "rơi đồ", chưa được {@code WorldFacadeTest} (chỉ verify server-world báo đúng người hạ gục) hay
 * {@code PostgresInventoryAdapterIntegrationTest} (chỉ verify DB) phủ tới.
 */
@Timeout(5)
class ConnectedCharacterRegistryTest {

    private ConnectedCharacterRegistry registry;

    @AfterEach
    void stopRegistry() {
        if (registry != null) {
            registry.stop();
        }
    }

    @Test
    void shouldSendItemGrantedEvent_whenLootRolledSuccessfully() throws InvalidProtocolBufferException {
        registry = new ConnectedCharacterRegistry(fakeInventoryFacade(100)); // 100% -> tất định, không flaky
        var channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(1L, channel.pipeline().firstContext());

        registry.onMonsterKilled(1L, "training_dummy");

        var frame = awaitOutboundFrame(channel);
        var event = ItemGrantedEvent.parseFrom(frame.payload());
        assertThat(frame.header().opcode()).isEqualTo(Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE);
        assertThat(event.getItemTemplateId()).isEqualTo(100L);
        assertThat(event.getQuantity()).isEqualTo(2);
    }

    @Test
    void shouldSendNothing_whenLootNeverDrops() {
        registry = new ConnectedCharacterRegistry(fakeInventoryFacade(0)); // 0% -> tất định không rơi gì
        var channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
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
        DropTablePort dropTablePort =
                dropTableCode -> List.of(new DropTableEntry(100L, dropChancePercent, 2, 2));
        var addItemUseCase = new AddItemUseCase(inventoryPort, action -> action.accept(null));
        var grantLootUseCase = new GrantLootUseCase(dropTablePort, inventoryPort, action -> action.accept(null));
        // Equip/unequip không dùng trong test này (chỉ verify grantLoot) — fake tối thiểu để compile.
        ItemTemplatePort itemTemplatePort = itemTemplateId -> Optional.empty();
        EquipmentPort equipmentPort = new EquipmentPort() {
            @Override
            public Optional<Equipment> findByCharacter(long characterId) {
                return Optional.of(new Equipment(characterId, Map.of()));
            }

            @Override
            public void save(Transaction tx, Equipment equipment) {
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
        var enhanceItemUseCase =
                new EnhanceItemUseCase(inventoryPort, goldPort, auditPort, action -> action.accept(null));
        return new InventoryFacade(addItemUseCase, grantLootUseCase, equipmentUseCases, enhanceItemUseCase);
    }

    /** onMonsterKilled đẩy việc sang Virtual Thread riêng (lootExecutor) — poll thay vì đọc ngay. */
    private static PacketCodec.Frame awaitOutboundFrame(EmbeddedChannel channel) {
        var deadline = Instant.now().plus(Duration.ofSeconds(4));
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
