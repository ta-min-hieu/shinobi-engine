package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.Opcode;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Inventory/Equipment (500-599, NETWORK.md §4).
 */
class InventoryPacketsTest {

    @Test
    void shouldRoundTripItemGrantedEvent() throws InvalidProtocolBufferException {
        var event = ItemGrantedEvent.newBuilder().setItemTemplateId(1L).setQuantity(3).build();
        var header = new PacketHeader(Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        var parsed = ItemGrantedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getItemTemplateId()).isEqualTo(1L);
        assertThat(parsed.getQuantity()).isEqualTo(3);
    }
}
