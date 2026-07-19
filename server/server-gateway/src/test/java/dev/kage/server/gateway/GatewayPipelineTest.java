package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.junit.jupiter.api.Test;

/**
 * Test TOÀN BỘ pipeline (framing + codec + handler) qua {@link EmbeddedChannel} — không cần
 * socket thật (TESTING.md §2: "server-gateway: Netty client giả kết nối thật qua Netty
 * embedded"). Cùng cấu trúc pipeline với {@link GatewayBootstrap} — nếu hai bên lệch nhau, đây
 * là chỗ phải đồng bộ lại.
 */
class GatewayPipelineTest {

    @Test
    void shouldReplyPongToPing_withSameSequenceId() throws InvalidProtocolBufferException {
        var channel = newPipeline();
        var ping = PingCommand.newBuilder().setClientTimestampMs(123L).build();
        var header = new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, 7);

        writeInboundFrame(channel, header, ping.toByteArray());
        var response = readOutboundFrame(channel);

        // Response PHẢI khớp sequenceId của Command (NETWORK.md §105 request/response correlation).
        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_PONG_VALUE);
        assertThat(response.header().sequenceId()).isEqualTo(7);

        var pong = PongResponse.parseFrom(response.payload());
        assertThat(pong.getClientTimestampMs()).isEqualTo(123L);
        assertThat(pong.getServerTimestampMs()).isGreaterThan(0);
    }

    @Test
    void shouldReplyConnectResponse_toConnectRequest() throws InvalidProtocolBufferException {
        var channel = newPipeline();
        var request = ConnectRequest.newBuilder().setToken("bat-ky-token-nao").build();
        var header = new PacketHeader(Opcode.OPCODE_CONNECT_REQUEST_VALUE, ProtocolVersion.CURRENT, 1);

        writeInboundFrame(channel, header, request.toByteArray());
        var response = readOutboundFrame(channel);

        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_CONNECT_RESPONSE_VALUE);
        var connectResponse = ConnectResponse.parseFrom(response.payload());
        assertThat(connectResponse.getSuccess()).isTrue();
        assertThat(connectResponse.getSessionId()).isNotBlank();
    }

    @Test
    void shouldReplyError_forUnknownOpcode() {
        var channel = newPipeline();
        var header = new PacketHeader(9999, ProtocolVersion.CURRENT, 5);

        writeInboundFrame(channel, header, new byte[0]);
        var response = readOutboundFrame(channel);

        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    private static EmbeddedChannel newPipeline() {
        // ⚠️ Phải khớp CHÍNH XÁC pipeline thật trong GatewayBootstrap — lệch ở đây là test giả.
        return new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4),
                new LengthFieldPrepender(4),
                new PacketFrameDecoder(),
                new PacketFrameEncoder(),
                new GatewayChannelHandler());
    }

    private static void writeInboundFrame(EmbeddedChannel channel, PacketHeader header, byte[] payload) {
        byte[] frameBytes = PacketCodec.encodeFrame(header, payload);
        ByteBuf buf = Unpooled.buffer(4 + frameBytes.length);
        buf.writeInt(frameBytes.length);
        buf.writeBytes(frameBytes);
        channel.writeInbound(buf);
    }

    private static PacketCodec.Frame readOutboundFrame(EmbeddedChannel channel) {
        // ⚠️ PHÁT HIỆN THẬT: LengthFieldPrepender là MessageToMessageEncoder<ByteBuf> — encode()
        // sinh ra HAI message riêng biệt (buffer length-prefix + buffer content gốc qua retain()),
        // không gộp thành 1 buffer. Qua socket TCP thật thì vô hình (TCP là stream, tự nối lại),
        // nhưng EmbeddedChannel.readOutbound() trả về từng message một lần gọi — phải gom đủ.
        ByteBuf combined = Unpooled.buffer();
        ByteBuf part;
        while ((part = channel.readOutbound()) != null) {
            combined.writeBytes(part);
            part.release();
        }
        assertThat(combined.readableBytes())
                .as("server phải trả lời — không có outbound message nào")
                .isGreaterThan(0);
        int length = combined.readInt();
        byte[] bytes = new byte[length];
        combined.readBytes(bytes);
        return PacketCodec.decodeFrame(bytes);
    }
}
