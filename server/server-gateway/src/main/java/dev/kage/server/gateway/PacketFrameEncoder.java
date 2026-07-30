package dev.kage.server.gateway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import dev.kage.protocol.wire.PacketCodec;

/** Mã hoá {@link PacketCodec.Frame} thành bytes — {@code LengthFieldPrepender} lo 4-byte length. */
final class PacketFrameEncoder extends MessageToByteEncoder<PacketCodec.Frame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, PacketCodec.Frame msg, ByteBuf out) {
        out.writeBytes(PacketCodec.encodeFrame(msg.header(), msg.payload()));
    }
}
