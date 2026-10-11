package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Một gói làm ném ngoại lệ không được làm sập client, và không được chặn các gói SAU nó (quyết định
 * chủ dự án 2026-10-11).
 */
class InboundPacketGuardTest {

    private static final int POISON_OPCODE = 4242;

    private final InboundPacketGuard guard = new InboundPacketGuard();

    @Test
    void shouldDropThePoisonPacket_andKeepHandlingTheOnesAfterIt() {
        List<Integer> handled = new ArrayList<>();

        for (int opcode : new int[] {1, POISON_OPCODE, 3}) {
            guard.handle(frame(opcode), frame -> {
                if (frame.header().opcode() == POISON_OPCODE) {
                    throw new IllegalStateException("giá trị enum lạ (giả lập)");
                }
                handled.add(frame.header().opcode());
            });
        }

        assertThat(handled).containsExactly(1, 3);
        assertThat(guard.droppedPackets()).isEqualTo(1);
    }

    @Test
    void shouldReportWhetherThePacketWasHandled() {
        assertThat(guard.handle(frame(1), frame -> {})).isTrue();
        assertThat(guard.handle(frame(POISON_OPCODE), frame -> {
                    throw new NullPointerException("id chưa từng thấy (giả lập)");
                }))
                .isFalse();
    }

    private static PacketCodec.Frame frame(int opcode) {
        return new PacketCodec.Frame(new PacketHeader(opcode, ProtocolVersion.CURRENT, 0), new byte[0]);
    }
}
