package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.GoldChangedEvent;
import dev.kage.protocol.Opcode;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Character (200-299, NETWORK.md §4).
 *
 * <p>File này sinh ra cùng {@code GoldChangedEvent} (ADR-035, opcode 209) nên hiện chỉ phủ packet
 * đó; các packet Character cũ hơn được phủ gián tiếp ở {@code GatewayPipelineTest} (server-gateway).
 */
class CharacterPacketsTest {

    @Test
    void shouldRoundTripGoldChangedEvent_whenSpending() throws InvalidProtocolBufferException {
        GoldChangedEvent event = GoldChangedEvent.newBuilder()
                .setGold(1500L)
                .setDelta(-500L)
                .setReasonKey("gold.reason.buy_item")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        PacketCodec.Frame decoded = PacketCodec.decodeFrame(frameBytes);
        GoldChangedEvent parsed = GoldChangedEvent.parseFrom(decoded.payload());

        assertThat(decoded.header()).isEqualTo(header);
        // gold là SỐ DƯ TUYỆT ĐỐI sau thay đổi, delta chỉ để hiện hiệu ứng "-500" (ADR-035 Decision-5).
        assertThat(parsed.getGold()).isEqualTo(1500L);
        assertThat(parsed.getDelta()).isEqualTo(-500L);
        assertThat(parsed.getReasonKey()).isEqualTo("gold.reason.buy_item");
    }

    @Test
    void shouldRoundTripGoldChangedEvent_whenEnteringWorld() throws InvalidProtocolBufferException {
        // Lúc vào world chưa có giao dịch nào: delta=0, chỉ đồng bộ số dư (ADR-035 Decision-3).
        GoldChangedEvent event = GoldChangedEvent.newBuilder()
                .setGold(0L)
                .setDelta(0L)
                .setReasonKey("gold.reason.enter_world")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        GoldChangedEvent parsed =
                GoldChangedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getGold()).isZero();
        assertThat(parsed.getDelta()).isZero();
        assertThat(parsed.getReasonKey()).isEqualTo("gold.reason.enter_world");
    }
}
