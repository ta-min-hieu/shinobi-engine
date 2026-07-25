package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.PingCommand;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test bắt buộc theo TESTING.md §6 và ROADMAP Phase 2 Exit Criteria — xác nhận
 * {@link PacketCodec} encode/decode nhất quán, cả header thuần lẫn payload protobuf thật.
 */
class PacketCodecTest {

    @Test
    void shouldRoundTripHeaderAndRawPayload() {
        PacketHeader header = new PacketHeader(42, ProtocolVersion.CURRENT, 12345);
        byte[] payload = {1, 2, 3, 4, 5};

        byte[] frameBytes = PacketCodec.encodeFrame(header, payload);
        PacketCodec.Frame decoded = PacketCodec.decodeFrame(frameBytes);

        // ⚠️ KHÔNG assertThat(decoded).isEqualTo(...) — Frame chứa byte[], equals() mặc định
        // của record so sánh THAM CHIẾU mảng (xem javadoc PacketCodec.Frame). So riêng từng field;
        // assertThat(byte[]) của AssertJ so NỘI DUNG mảng (khác Object.equals mặc định).
        assertThat(decoded.header()).isEqualTo(header);
        assertThat(decoded.payload()).isEqualTo(payload);
    }

    @Test
    void shouldRoundTripRealProtobufPayload() throws InvalidProtocolBufferException {
        PingCommand ping = PingCommand.newBuilder().setClientTimestampMs(1_700_000_000_000L).build();
        PacketHeader header = new PacketHeader(1, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, ping.toByteArray());
        PacketCodec.Frame decoded = PacketCodec.decodeFrame(frameBytes);
        PingCommand parsedPing = PingCommand.parseFrom(decoded.payload());

        assertThat(decoded.header()).isEqualTo(header);
        assertThat(parsedPing.getClientTimestampMs()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void shouldRejectFrameShorterThanHeader() {
        byte[] tooShort = {1, 2, 3};

        assertThatThrownBy(() -> PacketCodec.decodeFrame(tooShort))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void headerShouldRejectOpcodeOutOfUint16Range() {
        assertThatThrownBy(() -> new PacketHeader(70_000, ProtocolVersion.CURRENT, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void headerShouldRejectProtocolVersionOutOfUint8Range() {
        assertThatThrownBy(() -> new PacketHeader(1, 300, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
