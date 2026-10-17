package dev.kage.server.gateway;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler ĐẦU TIÊN của mọi kết nối — áp trần số kết nối mở cùng lúc theo IP của {@link IpAdmission}
 * (dòng 71(a), 2026-10-17). Đứng trước TLS để kết nối thừa bị đóng TRƯỚC khi tốn một lượt bắt tay.
 *
 * <p>Một instance dùng chung cho mọi kết nối ({@link ChannelHandler.Sharable}): trạng thái nằm trong
 * {@link IpAdmission}; cờ "kết nối này đã được đếm" nằm trên chính channel, để kết nối bị từ chối
 * không trừ nhầm lượt của kết nối khác lúc đóng.
 */
@ChannelHandler.Sharable
final class IpAdmissionHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(IpAdmissionHandler.class);

    private static final AttributeKey<Boolean> ADMITTED = AttributeKey.valueOf("kage.ipAdmitted");

    private final IpAdmission admission;

    IpAdmissionHandler(IpAdmission admission) {
        this.admission = admission;
    }

    // FutureReturnValueIgnored: ChannelFuture của close() — kết nối bị từ chối, không còn gì để làm khi
    // đóng hỏng, cùng lệ các lần ctx.close() khác của gateway.
    @SuppressWarnings("FutureReturnValueIgnored")
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (!admission.tryOpen(ctx.channel().remoteAddress())) {
            LOG.warn(
                    "Từ chối kết nối: IP {} đã đủ trần kết nối cùng lúc",
                    ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        ctx.channel().attr(ADMITTED).set(Boolean.TRUE);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (Boolean.TRUE.equals(ctx.channel().attr(ADMITTED).getAndSet(null))) {
            admission.close(ctx.channel().remoteAddress());
            ctx.fireChannelInactive();
        }
    }
}
