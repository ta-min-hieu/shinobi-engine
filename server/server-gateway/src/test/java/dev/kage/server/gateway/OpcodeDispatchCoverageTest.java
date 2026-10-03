package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Cổng build "mọi opcode client gửi đều có handler phía server" — chạy qua CHÍNH pipeline thật
 * ({@link GatewayPipelineTest#newPipeline()}), không đọc bảng {@code handlers} bằng phản chiếu.
 *
 * <p>Thêm một {@code *_COMMAND}/{@code *_REQUEST} vào {@code opcode_registry.proto} mà quên đăng ký
 * handler thì client nhận {@code error.unknown_opcode} cho một lệnh hoàn toàn hợp lệ — biên dịch vẫn
 * xanh, chỉ người chơi mới thấy. Chiều ngược lại cũng là lỗi: server nhận một gói server→client (vd
 * {@code GOLD_CHANGED_EVENT}) từ client thì phải coi là opcode lạ, không được xử lý như lệnh.
 *
 * <p>Payload RỖNG là cố ý: proto3 parse được mọi message từ mảng rỗng, nên handler nào tồn tại cũng
 * đi tới được bước kiểm phiên đăng nhập — thứ test này cần chỉ là "có người nhận", không phải lệnh
 * thành công.
 */
class OpcodeDispatchCoverageTest {

    private static final String UNKNOWN_OPCODE_KEY = "error.unknown_opcode";

    @Test
    void everyClientToServerOpcodeShouldHaveAServerHandler() throws InvalidProtocolBufferException {
        List<String> unhandled = new ArrayList<>();
        for (Opcode opcode : wireOpcodes()) {
            if (isClientToServer(opcode) && answeredAsUnknown(opcode)) {
                unhandled.add(opcode.name());
            }
        }
        assertThat(unhandled)
                .as("opcode client gửi mà server trả error.unknown_opcode")
                .isEmpty();
    }

    @Test
    void serverToClientOpcodesShouldBeRejectedWhenAClientSendsThem() throws InvalidProtocolBufferException {
        List<String> accepted = new ArrayList<>();
        for (Opcode opcode : wireOpcodes()) {
            if (!isClientToServer(opcode) && !answeredAsUnknown(opcode)) {
                accepted.add(opcode.name());
            }
        }
        assertThat(accepted)
                .as("gói server→client mà server lại nhận như một lệnh")
                .isEmpty();
    }

    /**
     * Khung bị TCP cắt làm nhiều mảnh vẫn ghép lại đúng, và nhiều khung dính trong MỘT lần đọc vẫn
     * tách đủ — hai hình dạng TCP thật sinh ra mà {@code EmbeddedChannel} một-khung-một-lần không bao
     * giờ tự tạo.
     */
    @Test
    void shouldReassembleFramesSplitAcrossReadsAndSplitFramesSharingOneRead() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        byte[] first = lengthPrefixedPing(11, 111L);
        byte[] second = lengthPrefixedPing(12, 222L);
        byte[] third = lengthPrefixedPing(13, 333L);
        byte[] stream = new byte[first.length + second.length + third.length];
        System.arraycopy(first, 0, stream, 0, first.length);
        System.arraycopy(second, 0, stream, first.length, second.length);
        System.arraycopy(third, 0, stream, first.length + second.length, third.length);

        // Mảnh 1: 2 byte đầu của length prefix. Mảnh 2: phần còn lại của khung 1 + nửa khung 2.
        // Mảnh 3: nửa sau khung 2 + trọn khung 3.
        int cutA = 2;
        int cutB = first.length + second.length / 2;
        writeSlice(channel, stream, 0, cutA);
        assertThat(GatewayPipelineTest.readOutboundFrames(channel)).isEmpty();
        writeSlice(channel, stream, cutA, cutB);
        writeSlice(channel, stream, cutB, stream.length);

        List<PacketCodec.Frame> replies = GatewayPipelineTest.readOutboundFrames(channel);
        assertThat(replies).extracting(frame -> frame.header().sequenceId()).containsExactly(11, 12, 13);
        List<Long> echoed = new ArrayList<>();
        for (PacketCodec.Frame reply : replies) {
            assertThat(reply.header().opcode()).isEqualTo(Opcode.OPCODE_PONG_VALUE);
            echoed.add(PongResponse.parseFrom(reply.payload()).getClientTimestampMs());
        }
        assertThat(echoed).containsExactly(111L, 222L, 333L);
        channel.finishAndReleaseAll();
    }

    private static boolean answeredAsUnknown(Opcode opcode) throws InvalidProtocolBufferException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        try {
            GatewayPipelineTest.writeInboundFrame(
                    channel, new PacketHeader(opcode.getNumber(), ProtocolVersion.CURRENT, 1), new byte[0]);
            for (PacketCodec.Frame frame : GatewayPipelineTest.readOutboundFrames(channel)) {
                if (frame.header().opcode() == Opcode.OPCODE_ERROR_VALUE
                        && UNKNOWN_OPCODE_KEY.equals(
                                ErrorResponse.parseFrom(frame.payload()).getErrorKey())) {
                    return true;
                }
            }
            return false;
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static byte[] lengthPrefixedPing(int sequenceId, long timestamp) {
        byte[] frame = PacketCodec.encodeFrame(
                new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, sequenceId),
                PingCommand.newBuilder().setClientTimestampMs(timestamp).build().toByteArray());
        ByteBuf buf = Unpooled.buffer(Integer.BYTES + frame.length);
        buf.writeInt(frame.length);
        buf.writeBytes(frame);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return bytes;
    }

    private static void writeSlice(EmbeddedChannel channel, byte[] stream, int from, int to) {
        channel.writeInbound(Unpooled.copiedBuffer(stream, from, to - from));
    }

    private static List<Opcode> wireOpcodes() {
        List<Opcode> opcodes = new ArrayList<>();
        for (Opcode opcode : Opcode.values()) {
            if (opcode != Opcode.UNRECOGNIZED && opcode != Opcode.OPCODE_UNSPECIFIED) {
                opcodes.add(opcode);
            }
        }
        return opcodes;
    }

    /** Cùng quy ước hướng với {@code OpcodeRegistryTest} ở shared-protocol (hậu tố tên). */
    private static boolean isClientToServer(Opcode opcode) {
        String name = opcode.name();
        return opcode == Opcode.OPCODE_PING || name.endsWith("_COMMAND") || name.endsWith("_REQUEST");
    }
}
