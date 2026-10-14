package dev.kage.client.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Gói giữ lại trong lúc chờ {@code SelectCharacterResponse} quay về ĐẦU hàng đợi của kết nối, đúng thứ
 * tự tới (chủ dự án chốt 2026-10-14, docs/README.md dòng 73(c)). Không mở socket: kết nối chưa
 * {@code start()} chỉ là hàng đợi rỗng.
 */
class HeldFramesReplayTest {

    @Test
    void shouldReplayHeldFramesInArrivalOrder_beforeAnythingElse() {
        GameConnection connection = new GameConnection("127.0.0.1", 1);
        HeldFrames held = new HeldFrames();
        PacketCodec.Frame first = frame(1);
        PacketCodec.Frame second = frame(2);
        held.hold(first);
        held.hold(second);

        int dropped = held.replayInto(connection);

        assertThat(dropped).isZero();
        assertThat(connection.pollInbound()).isSameAs(first);
        assertThat(connection.pollInbound()).isSameAs(second);
        assertThat(connection.pollInbound()).isNull();
        assertThat(held.size()).as("đã trả hết thì không giữ lại gì").isZero();
    }

    @Test
    void shouldStopHoldingAtTheCap_andReportHowManyWereDropped() {
        HeldFrames held = new HeldFrames();
        for (int i = 0; i < HeldFrames.MAX_HELD + 3; i++) {
            held.hold(frame(i));
        }

        assertThat(held.size()).isEqualTo(HeldFrames.MAX_HELD);
        assertThat(held.clear()).isEqualTo(3);
        assertThat(held.size()).isZero();
    }

    private static PacketCodec.Frame frame(int sequenceId) {
        return new PacketCodec.Frame(
                new PacketHeader(Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, ProtocolVersion.CURRENT, sequenceId),
                new byte[0]);
    }
}
