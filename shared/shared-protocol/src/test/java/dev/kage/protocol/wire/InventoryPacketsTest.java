package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.EquipItemCommand;
import dev.kage.protocol.EquipItemResponse;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.UnequipItemCommand;
import dev.kage.protocol.UnequipItemResponse;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Inventory/Equipment (500-599, NETWORK.md §4).
 */
class InventoryPacketsTest {

    @Test
    void shouldRoundTripItemGrantedEvent() throws InvalidProtocolBufferException {
        var event = ItemGrantedEvent.newBuilder().setItemTemplateId(1L).setQuantity(3).setItemId(42L).build();
        var header = new PacketHeader(Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        var parsed = ItemGrantedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getItemTemplateId()).isEqualTo(1L);
        assertThat(parsed.getQuantity()).isEqualTo(3);
        assertThat(parsed.getItemId()).isEqualTo(42L);
    }

    @Test
    void shouldRoundTripEquipItemCommand() throws InvalidProtocolBufferException {
        var command =
                EquipItemCommand.newBuilder().setItemId(42L).setSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON).build();
        var header = new PacketHeader(Opcode.OPCODE_EQUIP_ITEM_COMMAND_VALUE, ProtocolVersion.CURRENT, 9);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var decoded = PacketCodec.decodeFrame(frameBytes);
        var parsed = EquipItemCommand.parseFrom(decoded.payload());

        assertThat(decoded.header()).isEqualTo(header);
        assertThat(parsed.getItemId()).isEqualTo(42L);
        assertThat(parsed.getSlot()).isEqualTo(EquipmentSlot.EQUIPMENT_SLOT_WEAPON);
    }

    @Test
    void shouldRoundTripEquipItemResponse() throws InvalidProtocolBufferException {
        var response = EquipItemResponse.newBuilder()
                .setSuccess(false)
                .setErrorKey("error.wrong_equipment_slot")
                .build();
        var header = new PacketHeader(Opcode.OPCODE_EQUIP_ITEM_RESPONSE_VALUE, ProtocolVersion.CURRENT, 9);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = EquipItemResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSuccess()).isFalse();
        assertThat(parsed.getErrorKey()).isEqualTo("error.wrong_equipment_slot");
    }

    @Test
    void shouldRoundTripUnequipItemCommand() throws InvalidProtocolBufferException {
        var command = UnequipItemCommand.newBuilder().setSlot(EquipmentSlot.EQUIPMENT_SLOT_ARMOR).build();
        var header = new PacketHeader(Opcode.OPCODE_UNEQUIP_ITEM_COMMAND_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = UnequipItemCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSlot()).isEqualTo(EquipmentSlot.EQUIPMENT_SLOT_ARMOR);
    }

    @Test
    void shouldRoundTripUnequipItemResponse() throws InvalidProtocolBufferException {
        var response = UnequipItemResponse.newBuilder().setSuccess(true).setErrorKey("").build();
        var header = new PacketHeader(Opcode.OPCODE_UNEQUIP_ITEM_RESPONSE_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = UnequipItemResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSuccess()).isTrue();
    }
}
