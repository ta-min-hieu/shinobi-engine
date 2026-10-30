package dev.kage.server.gateway;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * Khoá ghi ra cho {@link EmbeddedChannel} khi có luồng KHÁC luồng test ghi vào kênh (gốc cổng fuzz chập
 * chờn 2026-10-25, tìm ra 2026-10-30).
 *
 * <p>{@code EmbeddedEventLoop.inEventLoop()} luôn trả {@code true}, nên {@code ctx.writeAndFlush} từ luồng
 * tick của một phó bản (vd. Trấn Yêu Tháp, ADR-113 — vào được bằng một lệnh rỗng) chạy THẲNG trên luồng đó
 * thay vì được đưa về event loop như Netty thật. {@code LengthFieldPrepender} ghi tiền tố độ dài và thân
 * thành HAI message, nên hai luồng ghi cùng lúc xếp ra "tiền tố A, tiền tố B, thân A, thân B" — bên đọc
 * gặp độ dài rác (có khi âm ⇒ {@code NegativeArraySizeException}). Kênh thật không bị: mọi lệnh ghi từ
 * ngoài được chuyển thành MỘT task trên event loop.
 *
 * <p>Đặt handler này NGAY TRƯỚC {@link GatewayChannelHandler} (mọi lệnh ghi của server đi từ ctx của
 * handler đó về phía head) để mã hoá + xếp hàng một frame diễn ra trọn trong khoá; bên đọc giữ cùng khoá
 * (chính đối tượng kênh) khi xả hàng đợi — xem {@code GatewayPipelineTest#readOutboundFrames}.
 */
final class SerializedOutboundWrites extends ChannelOutboundHandlerAdapter {

    /** Chèn khoá vào một kênh đã dựng pipeline giao thức ({@link GatewayServer#addProtocolHandlers}). */
    static void install(EmbeddedChannel channel) {
        channel.pipeline()
                .addBefore(
                        channel.pipeline().context(GatewayChannelHandler.class).name(),
                        "serialized-outbound-writes",
                        new SerializedOutboundWrites());
    }

    // FutureReturnValueIgnored: ctx.write trả lại chính promise đã nhận — kết quả đi qua promise đó.
    @SuppressWarnings("FutureReturnValueIgnored")
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        synchronized (ctx.channel()) {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        synchronized (ctx.channel()) {
            ctx.flush();
        }
    }
}
