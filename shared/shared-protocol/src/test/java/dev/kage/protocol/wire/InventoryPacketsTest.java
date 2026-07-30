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
import dev.kage.protocol.ItemGrantedEvent;
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
}
