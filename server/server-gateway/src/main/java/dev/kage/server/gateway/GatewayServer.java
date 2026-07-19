package dev.kage.server.gateway;

import dev.kage.account.api.AccountFacade;
import dev.kage.character.api.CharacterFacade;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    // Chờ auth đang xử lý dở xong trước khi đóng DB lúc shutdown (SERVER.md §10 bước 3-4).
    private static final int AUTH_SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final int requestedPort;
    private final AccountFacade accountFacade;
    private final CharacterFacade characterFacade;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ExecutorService authExecutor;
    private Channel serverChannel;

    /** @param port 0 = để hệ điều hành tự cấp cổng trống (dùng trong test, xem {@link #port()}). */
    public GatewayServer(int port, AccountFacade accountFacade, CharacterFacade characterFacade) {
        this.requestedPort = port;
        this.accountFacade = accountFacade;
        this.characterFacade = characterFacade;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        // Auth chạm Redis/PostgreSQL (blocking I/O) — Virtual Thread, KHÔNG chạy trên EventLoop
        // (SERVER.md §2). Không giới hạn ECS state nào ở đây nên không cần enqueue vào server-world.
        authExecutor = Executors.newVirtualThreadPerTaskExecutor();

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
                                .addLast(new GatewayChannelHandler(accountFacade, characterFacade, authExecutor));
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

    /**
     * Graceful shutdown (SERVER.md §10) — không kill -9. Thứ tự: (1) ngừng nhận kết nối mới,
     * (2) chờ auth đang xử lý dở xong (đang chạm DB — đóng đột ngột giữa chừng = lỗi nửa vời),
     * (3) đóng event loop. Caller ({@link GatewayBootstrap}) đóng DB/Redis SAU khi hàm này trả về.
     */
    public void stop() {
        LOG.info("Đang tắt server-gateway...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (authExecutor != null) {
            authExecutor.shutdown();
            awaitAuthExecutorTermination();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    private void awaitAuthExecutorTermination() {
        try {
            if (!authExecutor.awaitTermination(AUTH_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn("authExecutor chưa xong sau {}s — buộc dừng", AUTH_SHUTDOWN_TIMEOUT_SECONDS);
                authExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            authExecutor.shutdownNow();
        }
    }
}
