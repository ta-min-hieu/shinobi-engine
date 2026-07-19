package dev.kage.server.gateway;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xử lý packet hệ thống (Ping/Connect) TRỰC TIẾP trên Netty EventLoop — chấp nhận được vì đây là
 * logic RẺ, không chạm DB, không block (SERVER.md §2). Từ Phase 3 trở đi, Command chạm
 * gameplay/DB PHẢI enqueue vào server-world, KHÔNG xử lý inline ở đây.
 *
 * <p>⚠️ Phase 2: CONNECT_REQUEST luôn thành công (feature-account chưa tồn tại — Phase 3). Chỉ
 * để verify framing + handshake hoạt động, KHÔNG PHẢI auth thật (SECURITY.md, ADR-017 chưa chốt).
 */
final class GatewayChannelHandler extends SimpleChannelInboundHandler<PacketCodec.Frame> {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayChannelHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, PacketCodec.Frame frame) {
        var header = frame.header();
        try {
            if (header.opcode() == Opcode.OPCODE_PING_VALUE) {
                handlePing(ctx, header, frame.payload());
            } else if (header.opcode() == Opcode.OPCODE_CONNECT_REQUEST_VALUE) {
                handleConnect(ctx, header, frame.payload());
            } else {
                sendError(ctx, header.sequenceId(), 404, "error.unknown_opcode");
            }
        } catch (InvalidProtocolBufferException e) {
            // Fail fast ở MỨC PACKET (Điều 16) — không làm sập kết nối vì 1 payload hỏng.
            LOG.warn("Payload protobuf không hợp lệ: opcode={}, sequenceId={}",
                    header.opcode(), header.sequenceId(), e);
            sendError(ctx, header.sequenceId(), 400, "error.invalid_payload");
        }
    }

    private void handlePing(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var ping = PingCommand.parseFrom(payload);
        var pong = PongResponse.newBuilder()
                .setClientTimestampMs(ping.getClientTimestampMs())
                .setServerTimestampMs(System.currentTimeMillis())
                .build();
        send(ctx, Opcode.OPCODE_PONG_VALUE, header.sequenceId(), pong.toByteArray());
    }

    private void handleConnect(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var request = ConnectRequest.parseFrom(payload);
        // ⚠️ Không validate token thật — xem javadoc lớp này. Đọc field để chắc parse đúng schema.
        LOG.info("Kết nối mới: tokenLength={} (chưa validate — Phase 2 stub)", request.getToken().length());

        var sessionId = UUID.randomUUID().toString();
        var response = ConnectResponse.newBuilder()
                .setSuccess(true)
                .setSessionId(sessionId)
                .build();
        send(ctx, Opcode.OPCODE_CONNECT_RESPONSE_VALUE, header.sequenceId(), response.toByteArray());
    }

    private void sendError(ChannelHandlerContext ctx, int relatedSequenceId, int errorCode, String errorKey) {
        var error = ErrorResponse.newBuilder()
                .setErrorCode(errorCode)
                .setErrorKey(errorKey)
                .setRelatedSequenceId(relatedSequenceId)
                .build();
        send(ctx, Opcode.OPCODE_ERROR_VALUE, relatedSequenceId, error.toByteArray());
    }

    private void send(ChannelHandlerContext ctx, int opcode, int sequenceId, byte[] payload) {
        var header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        ctx.writeAndFlush(new PacketCodec.Frame(header, payload));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.error("Lỗi không mong đợi trên kết nối {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
