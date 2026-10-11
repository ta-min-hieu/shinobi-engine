package dev.kage.server.gateway;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server TCP có thể start/stop độc lập (tách khỏi {@code main()} để test end-to-end được bằng
 * socket thật — không chỉ {@code EmbeddedChannel}). {@link GatewayBootstrap} chỉ là entrypoint
 * mỏng gọi lớp này.
 *
 * <p>Pipeline: [TLS nếu {@link TlsConfig#enabled()}, ADR-027] → framing length-prefixed
 * (NETWORK.md §101) → {@code PacketCodec} → hạn im lặng (NETWORK.md §10) →
 * {@link GatewayChannelHandler}, xem {@link #addProtocolHandlers}. MODULE DUY NHẤT được
 * import Netty (Điều 23).
 */
public final class GatewayServer {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayServer.class);

    // Chống packet khổng lồ chiếm bộ nhớ (một dạng rate-limit thô — NETWORK.md §109). 64 KiB từ
    // 2026-10-11 (quyết định chủ dự án; trước đó 1 MiB): đây là trần khung client GỬI LÊN, và lệnh hợp lệ
    // lớn nhất (thư 1024 ký tự kèm đính kèm, lời chào giao dịch 20 dòng) chỉ vài KiB — phần còn lại của
    // 1 MiB chỉ có lợi cho kẻ bơm khung lớn. Chiều server → client KHÔNG qua trần này (catalog vật phẩm
    // lớn hơn nhiều); client tự giữ trần đọc vào riêng của nó.
    private static final int MAX_FRAME_LENGTH = 64 * 1024;
    private static final int LENGTH_FIELD_LENGTH = 4;

    // NETWORK.md §10: client ping mỗi ~15s; server không nhận gì trong ~45s ⇒ kết nối chết, dọn dẹp
    // ("bắt buộc — TCP có thể treo mà không báo lỗi"). Trước 2026-10-02 server KHÔNG có hạn này: một
    // kết nối nửa mở (rút dây mạng, NAT quên luồng) giữ nhân vật đứng trong world mãi mãi, và một
    // kết nối không bao giờ đăng nhập giữ socket + handler mãi mãi. Mọi frame đều tính, không riêng Ping.
    static final Duration READER_IDLE_TIMEOUT = Duration.ofSeconds(45);

    // Số kết nối TCP chờ accept() tối đa (hàng đợi SYN đã hoàn tất handshake, chưa được boss thread
    // nhận). PHÁT HIỆN THẬT (BENCHMARK.md §5.4, 2026-07-29): giá trị cũ 128 chính là nguyên nhân
    // 149-856 lỗi client "Connection refused" khi load-test burst-connect tức thời N=1000/2000 —
    // KHÔNG phải do OS default (dòng log Netty "Failed to get SOMAXCONN ... Default: 200" chỉ là
    // Netty tự dò backlog mặc định của HĐH khi KHÔNG set SO_BACKLOG tường minh; code này LUÔN set
    // tường minh nên giá trị áp dụng thật là 128, không phải 200). 1024 là khởi điểm hợp lý hơn hẳn
    // (thấy rõ ràng ở BENCHMARK.md §5.4, chưa test tới điểm gãy mới) — CÙNG tinh thần HikariCP
    // 10→20 (Điều 30: đo trước, tinh chỉnh sau bằng số liệu thật, không đoán số cuối cùng ngay).
    // ⚠️ Giá trị THẬT sự áp dụng còn bị OS clamp thêm (Linux: net.core.somaxconn lúc deploy thật,
    // DOCKER.md — ngoài phạm vi code Java này) — set cao ở đây là điều kiện CẦN, không phải ĐỦ.
    private static final int SO_BACKLOG = 1024;

    // Chờ auth đang xử lý dở xong trước khi đóng DB lúc shutdown (SERVER.md §10 bước 3-4).
    private static final int AUTH_SHUTDOWN_TIMEOUT_SECONDS = 10;

    // "Quiet period" mặc định của Netty (2s) nhắm vào traffic production đang settle — không cần
    // thiết ở quy mô cá nhân, chỉ làm chậm test/shutdown vô ích. Timeout là mức trần an toàn.
    private static final long EVENT_LOOP_QUIET_PERIOD_MILLIS = 100;
    private static final long EVENT_LOOP_SHUTDOWN_TIMEOUT_MILLIS = 5000;

    /**
     * Gộp {@code worldGateway} + {@code observability} — Checkstyle ParameterNumber tối đa 4 tham
     * số cho constructor (cùng lý do các record {@code *Context} ở {@link GatewayBootstrap}).
     *
     * @param worldGateway {@code registry()} đã đăng ký làm {@code WorldOutboundSink} của
     *     {@code facade()} TRƯỚC khi truyền vào đây (composition root — {@code GatewayBootstrap}).
     */
    public record GatewayContext(WorldGateway worldGateway, GatewayObservability observability) {}

    private final int requestedPort;
    private final GatewayFacades facades;
    private final GatewayContext context;
    private final TlsConfig tlsConfig;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ExecutorService authExecutor;
    private Channel serverChannel;

    /**
     * @param port 0 = để hệ điều hành tự cấp cổng trống (dùng trong test, xem {@link #port()}).
     * @param tlsConfig ADR-027 — {@link TlsConfig#disabled()} chỉ dùng cho test/dev cục bộ.
     */
    public GatewayServer(int port, GatewayFacades facades, GatewayContext context, TlsConfig tlsConfig) {
        this.requestedPort = port;
        this.facades = facades;
        this.context = context;
        this.tlsConfig = tlsConfig;
    }

    /**
     * Mở cổng. Ném ra (cổng đang bị chiếm, chứng chỉ TLS hỏng...) thì mọi thứ đã dựng trong lúc mở
     * được tắt lại trước khi ném — xem {@link #releaseAfterFailedStart}.
     */
    public void start() throws InterruptedException, SSLException {
        boolean started = false;
        try {
            bindAndListen();
            started = true;
        } finally {
            if (!started) {
                releaseAfterFailedStart();
            }
        }
    }

    private void bindAndListen() throws InterruptedException, SSLException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        // Auth chạm Redis/PostgreSQL (blocking I/O) — Virtual Thread, KHÔNG chạy trên EventLoop
        // (SERVER.md §2). Không giới hạn ECS state nào ở đây nên không cần enqueue vào server-world.
        authExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // ADR-027: 1 SslContext dùng chung cho mọi channel (không tạo lại mỗi kết nối) — mỗi
        // channel tự có SslHandler RIÊNG qua sslContext.newHandler(alloc) bên dưới (state
        // handshake là per-channel, SslContext chỉ giữ cert/key dùng chung).
        SslContext sslContext = buildSslContext();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, SO_BACKLOG)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        // TLS PHẢI là handler ĐẦU TIÊN — mọi byte sau đó (framing, packet) đi qua
                        // lớp mã hoá trước khi tới các handler khác.
                        if (sslContext != null) {
                            pipeline.addLast(sslContext.newHandler(ch.alloc()));
                        }
                        addProtocolHandlers(
                                pipeline,
                                new GatewayChannelHandler(
                                        facades, context.worldGateway(), authExecutor, context.observability()));
                    }
                });

        serverChannel = bootstrap.bind(requestedPort).sync().channel();
        LOG.info("server-gateway lắng nghe cổng {} (TLS {})", port(), tlsConfig.enabled() ? "bật" : "TẮT");
    }

    /**
     * Mọi handler SAU lớp TLS — tách ra để test {@code EmbeddedChannel} dựng ĐÚNG pipeline này thay
     * vì chép tay một bản (bản chép tay không có {@link IdleStateHandler}, nên không test nào thấy
     * server thiếu nó).
     *
     * <p>{@link IdleStateHandler} đứng SAU {@link PacketFrameDecoder}: chỉ một frame HOÀN CHỈNH mới
     * tính là "còn sống" — client nhỏ giọt từng byte của một frame không bao giờ xong không giữ được
     * kết nối. Hết hạn thì {@code GatewayChannelHandler#userEventTriggered} đóng kết nối, và
     * {@code channelInactive} dọn nhân vật như mọi lần ngắt khác.
     */
    static void addProtocolHandlers(ChannelPipeline pipeline, GatewayChannelHandler handler) {
        pipeline.addLast(new LengthFieldBasedFrameDecoder(
                        MAX_FRAME_LENGTH, 0, LENGTH_FIELD_LENGTH, 0, LENGTH_FIELD_LENGTH))
                .addLast(new LengthFieldPrepender(LENGTH_FIELD_LENGTH))
                .addLast(new PacketFrameDecoder())
                .addLast(new PacketFrameEncoder())
                .addLast(new IdleStateHandler(READER_IDLE_TIMEOUT.toMillis(), 0, 0, TimeUnit.MILLISECONDS))
                .addLast(handler);
    }

    /** {@code null} nếu {@link TlsConfig#enabled()} là false (ADR-027 — test/dev tường minh). */
    private SslContext buildSslContext() throws SSLException {
        if (!tlsConfig.enabled()) {
            return null;
        }
        return SslContextBuilder.forServer(tlsConfig.certChainFile(), tlsConfig.keyFile())
                .build();
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
     * (2) báo người chơi đang kết nối, (3) đóng event loop — TRIGGER {@code channelInactive} cho
     * mọi channel, nơi lưu vị trí cuối chạy (SERVER.md §8/§10 bước 4) — rồi (4) mới tắt
     * {@code authExecutor}. Caller ({@link GatewayBootstrap}) đóng DB/Redis SAU khi hàm này trả về.
     *
     * <p>⚠️ PHÁT HIỆN THẬT: bản trước tắt {@code authExecutor} TRƯỚC khi đóng event loop —
     * {@code channelInactive} (chạy lúc event loop đóng channel) cố enqueue lưu vị trí vào 1
     * executor ĐÃ shutdown, bị {@code RejectedExecutionException} âm thầm. Đảo thứ tự đúng lại.
     */
    public void stop() {
        LOG.info("Đang tắt server-gateway...");
        // isOpen: stop() gọi lần hai (event loop đã tắt) thì close() trả future THẤT BẠI — chờ nó là ném.
        if (serverChannel != null && serverChannel.isOpen()) {
            // Chờ đóng XONG: bước (1) phải hoàn tất trước khi báo người chơi ở bước (2), không thì
            // một kết nối mới vẫn có thể lọt vào giữa lúc broadcast và không nhận được thông báo.
            serverChannel.close().syncUninterruptibly();
        }
        context.worldGateway().registry().broadcastShuttingDown();
        awaitGracefully(workerGroup);
        awaitGracefully(bossGroup);
        if (authExecutor != null) {
            authExecutor.shutdown();
            awaitAuthExecutorTermination();
        }
    }

    /**
     * Mở cổng thất bại (sửa 2026-10-03): {@code bind} đã kịp chạy luồng boss của Netty — luồng
     * KHÔNG daemon — nên bản cũ ném ra rồi để nó sống mãi: {@code main()} chết nhưng JVM không thoát,
     * tiến trình treo mà không phục vụ ai (restart policy của Docker không bao giờ kích hoạt). Không
     * báo người chơi gì: chưa ai kết nối được. Xem {@code GatewayServerStartFailureTest}.
     */
    private void releaseAfterFailedStart() {
        awaitGracefully(workerGroup);
        awaitGracefully(bossGroup);
        if (authExecutor != null) {
            authExecutor.shutdownNow();
        }
    }

    private static void awaitGracefully(EventLoopGroup group) {
        if (group != null) {
            group.shutdownGracefully(
                            EVENT_LOOP_QUIET_PERIOD_MILLIS, EVENT_LOOP_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .awaitUninterruptibly();
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
