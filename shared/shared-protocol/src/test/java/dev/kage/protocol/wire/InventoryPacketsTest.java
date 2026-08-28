package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.EnhanceItemCommand;
import dev.kage.protocol.EnhanceItemResponse;
import dev.kage.protocol.EnhanceOutcome;
import dev.kage.protocol.EquipItemCommand;
import dev.kage.protocol.EquipItemResponse;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.EquippedSlotView;
import dev.kage.protocol.GetItemCatalogRequest;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.UnequipItemCommand;
import dev.kage.protocol.UnequipItemResponse;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Inventory/Equipment (500-599, NETWORK.md §4).
 */
class InventoryPacketsTest {

    @Test
    void shouldRoundTripItemGrantedEvent() throws InvalidProtocolBufferException {
        ItemGrantedEvent event = ItemGrantedEvent.newBuilder()
                .setItemTemplateId(1L)
                .setQuantity(3)
                .setItemId(42L)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        ItemGrantedEvent parsed =
                ItemGrantedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getItemTemplateId()).isEqualTo(1L);
        assertThat(parsed.getQuantity()).isEqualTo(3);
        assertThat(parsed.getItemId()).isEqualTo(42L);
    }

    @Test
    void shouldRoundTripEquipItemCommand() throws InvalidProtocolBufferException {
        EquipItemCommand command = EquipItemCommand.newBuilder()
                .setItemId(42L)
                .setSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_EQUIP_ITEM_COMMAND_VALUE, ProtocolVersion.CURRENT, 9);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        PacketCodec.Frame decoded = PacketCodec.decodeFrame(frameBytes);
        EquipItemCommand parsed = EquipItemCommand.parseFrom(decoded.payload());

        assertThat(decoded.header()).isEqualTo(header);
        assertThat(parsed.getItemId()).isEqualTo(42L);
        assertThat(parsed.getSlot()).isEqualTo(EquipmentSlot.EQUIPMENT_SLOT_WEAPON);
    }

    @Test
    void shouldRoundTripEquipItemResponse() throws InvalidProtocolBufferException {
        EquipItemResponse response = EquipItemResponse.newBuilder()
                .setSuccess(false)
                .setErrorKey("error.wrong_equipment_slot")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_EQUIP_ITEM_RESPONSE_VALUE, ProtocolVersion.CURRENT, 9);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        EquipItemResponse parsed =
                EquipItemResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSuccess()).isFalse();
        assertThat(parsed.getErrorKey()).isEqualTo("error.wrong_equipment_slot");
    }

    @Test
    void shouldRoundTripUnequipItemCommand() throws InvalidProtocolBufferException {
        UnequipItemCommand command = UnequipItemCommand.newBuilder()
                .setSlot(EquipmentSlot.EQUIPMENT_SLOT_ARMOR)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_UNEQUIP_ITEM_COMMAND_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        UnequipItemCommand parsed =
                UnequipItemCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSlot()).isEqualTo(EquipmentSlot.EQUIPMENT_SLOT_ARMOR);
    }

    @Test
    void shouldRoundTripUnequipItemResponse() throws InvalidProtocolBufferException {
        UnequipItemResponse response = UnequipItemResponse.newBuilder()
                .setSuccess(true)
                .setErrorKey("")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_UNEQUIP_ITEM_RESPONSE_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        UnequipItemResponse parsed = UnequipItemResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSuccess()).isTrue();
    }

    @Test
    void shouldRoundTripEnhanceItemCommand() throws InvalidProtocolBufferException {
        EnhanceItemCommand command = EnhanceItemCommand.newBuilder()
                .setItemId(42L)
                .setUseProtection(true)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ENHANCE_ITEM_COMMAND_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        EnhanceItemCommand parsed =
                EnhanceItemCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getItemId()).isEqualTo(42L);
        assertThat(parsed.getUseProtection()).isTrue();
    }

    @Test
    void shouldRoundTripEnhanceItemResponse() throws InvalidProtocolBufferException {
        EnhanceItemResponse response = EnhanceItemResponse.newBuilder()
                .setOutcome(EnhanceOutcome.ENHANCE_OUTCOME_FAILED_DROPPED)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ENHANCE_ITEM_RESPONSE_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        EnhanceItemResponse parsed = EnhanceItemResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(EnhanceOutcome.ENHANCE_OUTCOME_FAILED_DROPPED);
    }

    @Test
    void shouldRoundTripEnhanceItemResponse_withProtectedOutcome() throws InvalidProtocolBufferException {
        EnhanceItemResponse response = EnhanceItemResponse.newBuilder()
                .setOutcome(EnhanceOutcome.ENHANCE_OUTCOME_FAILED_PROTECTED)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ENHANCE_ITEM_RESPONSE_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        EnhanceItemResponse parsed = EnhanceItemResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(EnhanceOutcome.ENHANCE_OUTCOME_FAILED_PROTECTED);
    }

    // --- ADR-035 (Accepted 2026-08-27): liệt kê túi đồ 507/508 + catalog vật phẩm 509/510 ---

    @Test
    void shouldRoundTripListInventoryRequest() throws InvalidProtocolBufferException {
        ListInventoryRequest request = ListInventoryRequest.newBuilder().build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE, ProtocolVersion.CURRENT, 11);

        byte[] frameBytes = PacketCodec.encodeFrame(header, request.toByteArray());
        PacketCodec.Frame decoded = PacketCodec.decodeFrame(frameBytes);

        // Không field nào (characterId từ session) — round-trip vẫn phải giữ nguyên header.
        assertThat(decoded.header()).isEqualTo(header);
        assertThat(ListInventoryRequest.parseFrom(decoded.payload())).isEqualTo(request);
    }

    @Test
    void shouldRoundTripInventoryItemView() throws InvalidProtocolBufferException {
        InventoryItemView view = InventoryItemView.newBuilder()
                .setItemId(42L)
                .setItemTemplateId(2L)
                .setQuantity(7)
                .setEnhanceLevel(3)
                .setSlotIndex(5)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE, ProtocolVersion.CURRENT, 11);

        byte[] frameBytes = PacketCodec.encodeFrame(header, view.toByteArray());
        InventoryItemView parsed =
                InventoryItemView.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getItemId()).isEqualTo(42L);
        assertThat(parsed.getItemTemplateId()).isEqualTo(2L);
        assertThat(parsed.getQuantity()).isEqualTo(7);
        assertThat(parsed.getEnhanceLevel()).isEqualTo(3);
        assertThat(parsed.getSlotIndex()).isEqualTo(5);
    }

    @Test
    void shouldRoundTripEquippedSlotView() throws InvalidProtocolBufferException {
        EquippedSlotView view = EquippedSlotView.newBuilder()
                .setSlot(EquipmentSlot.EQUIPMENT_SLOT_ACCESSORY_2)
                .setItemId(99L)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE, ProtocolVersion.CURRENT, 11);

        byte[] frameBytes = PacketCodec.encodeFrame(header, view.toByteArray());
        EquippedSlotView parsed =
                EquippedSlotView.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSlot()).isEqualTo(EquipmentSlot.EQUIPMENT_SLOT_ACCESSORY_2);
        assertThat(parsed.getItemId()).isEqualTo(99L);
    }

    @Test
    void shouldRoundTripListInventoryResponse() throws InvalidProtocolBufferException {
        ListInventoryResponse response = ListInventoryResponse.newBuilder()
                .addItems(InventoryItemView.newBuilder()
                        .setItemId(42L)
                        .setItemTemplateId(2L)
                        .setQuantity(1)
                        .setEnhanceLevel(4)
                        .setSlotIndex(0)
                        .build())
                .setCapacity(20)
                .addEquipped(EquippedSlotView.newBuilder()
                        .setSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON)
                        .setItemId(42L)
                        .build())
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE, ProtocolVersion.CURRENT, 11);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ListInventoryResponse parsed = ListInventoryResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getCapacity()).isEqualTo(20);
        assertThat(parsed.getItemsList()).hasSize(1);
        assertThat(parsed.getItems(0).getItemId()).isEqualTo(42L);
        assertThat(parsed.getItems(0).getEnhanceLevel()).isEqualTo(4);
        // Trang bị đi CÙNG response túi đồ (ADR-035 Decision-1) — không có LIST_EQUIPMENT riêng.
        assertThat(parsed.getEquippedList()).hasSize(1);
        assertThat(parsed.getEquipped(0).getSlot()).isEqualTo(EquipmentSlot.EQUIPMENT_SLOT_WEAPON);
        assertThat(parsed.getEquipped(0).getItemId()).isEqualTo(42L);
    }

    @Test
    void shouldRoundTripGetItemCatalogRequest() throws InvalidProtocolBufferException {
        GetItemCatalogRequest request =
                GetItemCatalogRequest.newBuilder().setKnownVersion("abc123").build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_GET_ITEM_CATALOG_REQUEST_VALUE, ProtocolVersion.CURRENT, 12);

        byte[] frameBytes = PacketCodec.encodeFrame(header, request.toByteArray());
        GetItemCatalogRequest parsed = GetItemCatalogRequest.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getKnownVersion()).isEqualTo("abc123");
    }

    @Test
    void shouldRoundTripItemCatalogEntry() throws InvalidProtocolBufferException {
        ItemCatalogEntry entry = ItemCatalogEntry.newBuilder()
                .setId(2L)
                .setCode("wooden_sword")
                .setNameKey("item.wooden_sword.name")
                .setMaxStack(1)
                .setEquipSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON)
                .setBonusAtk(5)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ITEM_CATALOG_RESPONSE_VALUE, ProtocolVersion.CURRENT, 12);

        byte[] frameBytes = PacketCodec.encodeFrame(header, entry.toByteArray());
        ItemCatalogEntry parsed =
                ItemCatalogEntry.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getId()).isEqualTo(2L);
        assertThat(parsed.getCode()).isEqualTo("wooden_sword");
        assertThat(parsed.getNameKey()).isEqualTo("item.wooden_sword.name");
        assertThat(parsed.getMaxStack()).isEqualTo(1);
        assertThat(parsed.getEquipSlot()).isEqualTo(EquipmentSlot.EQUIPMENT_SLOT_WEAPON);
        assertThat(parsed.getBonusAtk()).isEqualTo(5);
    }

    @Test
    void shouldRoundTripItemCatalogResponse_withFullCatalog() throws InvalidProtocolBufferException {
        ItemCatalogResponse response = ItemCatalogResponse.newBuilder()
                .setUnchanged(false)
                .setVersion("v-hash-1")
                .addEntries(ItemCatalogEntry.newBuilder()
                        .setId(1L)
                        .setCode("kunai")
                        .setNameKey("item.kunai.name")
                        .setMaxStack(99)
                        // equip_slot bỏ trống = UNSPECIFIED = không mặc được (khớp equip_slot NULL).
                        .setBonusAtk(0)
                        .build())
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ITEM_CATALOG_RESPONSE_VALUE, ProtocolVersion.CURRENT, 12);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ItemCatalogResponse parsed = ItemCatalogResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getUnchanged()).isFalse();
        assertThat(parsed.getVersion()).isEqualTo("v-hash-1");
        assertThat(parsed.getEntriesList()).hasSize(1);
        assertThat(parsed.getEntries(0).getCode()).isEqualTo("kunai");
        assertThat(parsed.getEntries(0).getEquipSlot()).isEqualTo(EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED);
    }

    /**
     * Nhánh {@code unchanged=true} chỉ chạy từ lần đăng nhập THỨ HAI trở đi nên rất dễ mục nếu
     * không có test riêng (ADR-035 Consequences) — giữ test này kể cả khi trông thừa.
     */
    @Test
    void shouldRoundTripItemCatalogResponse_whenUnchanged() throws InvalidProtocolBufferException {
        ItemCatalogResponse response = ItemCatalogResponse.newBuilder()
                .setUnchanged(true)
                .setVersion("v-hash-1")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ITEM_CATALOG_RESPONSE_VALUE, ProtocolVersion.CURRENT, 12);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ItemCatalogResponse parsed = ItemCatalogResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getUnchanged()).isTrue();
        assertThat(parsed.getVersion()).isEqualTo("v-hash-1");
        assertThat(parsed.getEntriesList()).isEmpty();
    }
}
