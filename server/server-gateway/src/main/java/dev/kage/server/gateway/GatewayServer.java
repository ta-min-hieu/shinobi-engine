package dev.kage.server.gateway;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server TCP có thể start/stop độc lập (tách khỏi {@code main()} để test end-to-end được bằng
 * socket thật — không chỉ {@code EmbeddedChannel}). {@link GatewayBootstrap} chỉ là entrypoint
 * mỏng gọi lớp này.
 *
 * <p>Pipeline: framing length-prefixed (NETWORK.md §101) → {@code PacketCodec} →
 * {@link GatewayChannelHandler}. MODULE DUY NHẤT được import Netty (Điều 23).
 */
public final class GatewayServer {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayServer.class);

    // Chống packet khổng lồ chiếm bộ nhớ (một dạng rate-limit thô — NETWORK.md §109).
    private static final int MAX_FRAME_LENGTH = 1024 * 1024;
    private static final int LENGTH_FIELD_LENGTH = 4;

    private final int requestedPort;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /** @param port 0 = để hệ điều hành tự cấp cổng trống (dùng trong test, xem {@link #port()}). */
    public GatewayServer(int port) {
        this.requestedPort = port;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        var bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder(
                                        MAX_FRAME_LENGTH, 0, LENGTH_FIELD_LENGTH, 0, LENGTH_FIELD_LENGTH))
                                .addLast(new LengthFieldPrepender(LENGTH_FIELD_LENGTH))
                                .addLast(new PacketFrameDecoder())
                                .addLast(new PacketFrameEncoder())
                                .addLast(new GatewayChannelHandler());
                    }
                });

        serverChannel = bootstrap.bind(requestedPort).sync().channel();
        LOG.info("server-gateway lắng nghe cổng {}", port());
    }

    /** Cổng thật đang lắng nghe — khác {@code requestedPort} nếu tạo bằng port 0. */
    public int port() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public void awaitTermination() throws InterruptedException {
        serverChannel.closeFuture().sync();
    }

    /** Graceful shutdown (SERVER.md §10) — không kill -9, đóng kết nối + event loop sạch. */
    public void stop() {
        LOG.info("Đang tắt server-gateway...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }
}
