package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ServerShuttingDownEvent;
import org.junit.jupiter.api.Test;

/** Round-trip test bắt buộc khi thêm packet mới (shared/CLAUDE.md). */
class ServerShuttingDownEventTest {

    @Test
    void shouldRoundTripServerShuttingDownEvent() throws InvalidProtocolBufferException {
        var event = ServerShuttingDownEvent.newBuilder().build();
        var header = new PacketHeader(Opcode.OPCODE_SERVER_SHUTTING_DOWN_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        var decoded = PacketCodec.decodeFrame(frameBytes);

        assertThat(decoded.header()).isEqualTo(header);
        assertThat(ServerShuttingDownEvent.parseFrom(decoded.payload())).isNotNull();
    }
}
