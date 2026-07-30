package dev.kage.server.gateway;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import dev.kage.protocol.wire.PacketCodec;

/**
 * Giải mã frame (đã được {@code LengthFieldBasedFrameDecoder} tách khỏi 4-byte length prefix)
 * thành {@link PacketCodec.Frame} bằng codec DÙNG CHUNG với client-core (NETWORK.md §102).
 */
final class PacketFrameDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        byte[] frameBytes = new byte[msg.readableBytes()];
        msg.readBytes(frameBytes);
        out.add(PacketCodec.decodeFrame(frameBytes));
    }
}
