package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Mở cổng thất bại không được để lại luồng không-daemon nào (2026-10-03).
 *
 * <p>Bug: {@code bind} chạy luồng boss của Netty (KHÔNG daemon) rồi mới phát hiện cổng đã bị chiếm.
 * {@link GatewayServer#start()} ném ra nhưng không tắt nhóm luồng đó, và {@code GatewayBootstrap}
 * cũng không tắt thứ nó đã dựng trước đó (luồng lưu vị trí, máy chủ metrics) — {@code main()} chết
 * mà JVM không bao giờ thoát: tiến trình treo, không phục vụ ai, Docker không restart.
 */
@Timeout(30)
class GatewayServerStartFailureTest {

    private static final String NETTY_THREAD_PREFIX = "nioEventLoopGroup";
    private static final long THREAD_EXIT_WAIT_MILLIS = 2_000L;

    @Test
    void shouldLeaveNoNonDaemonNettyThread_whenPortIsAlreadyTaken() throws IOException, InterruptedException {
        try (ServerSocketChannel occupied = ServerSocketChannel.open()) {
            occupied.bind(new InetSocketAddress(0));
            Set<Thread> before = Thread.getAllStackTraces().keySet();
            // Mở cổng thất bại TRƯỚC mọi kết nối: facade/context không được chạm tới.
            GatewayServer server = serverOn(occupied);

            assertThatThrownBy(server::start).isInstanceOf(java.net.BindException.class);

            assertThat(leakedNettyThreads(before)).isEmpty();
        }
    }

    @Test
    void shouldRunShutdown_whenServerFailsToStart() throws IOException {
        try (ServerSocketChannel occupied = ServerSocketChannel.open()) {
            occupied.bind(new InetSocketAddress(0));
            GatewayServer server = serverOn(occupied);
            AtomicInteger shutdownRuns = new AtomicInteger();

            assertThatThrownBy(() -> GatewayBootstrap.startOrShutDown(server, shutdownRuns::incrementAndGet))
                    .isInstanceOf(java.net.BindException.class);

            assertThat(shutdownRuns.get()).isEqualTo(1);
        }
    }

    /** Server muốn mở đúng cổng {@code occupied} đang giữ — facade/context là null vì không ai kết nối được. */
    private static GatewayServer serverOn(ServerSocketChannel occupied) throws IOException {
        int port = ((InetSocketAddress) occupied.getLocalAddress()).getPort();
        return new GatewayServer(port, null, null, TlsConfig.disabled());
    }

    /** Luồng Netty không-daemon sinh ra sau {@code before} và còn sống sau một khoảng chờ ngắn. */
    private static List<Thread> leakedNettyThreads(Set<Thread> before) throws InterruptedException {
        long deadline = System.currentTimeMillis() + THREAD_EXIT_WAIT_MILLIS;
        List<Thread> leaked = currentLeaks(before);
        while (!leaked.isEmpty() && System.currentTimeMillis() < deadline) {
            for (Thread thread : leaked) {
                thread.join(THREAD_EXIT_WAIT_MILLIS / 10);
            }
            leaked = currentLeaks(before);
        }
        return leaked;
    }

    private static List<Thread> currentLeaks(Set<Thread> before) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> !before.contains(thread))
                .filter(thread -> thread.getName().startsWith(NETTY_THREAD_PREFIX))
                .filter(thread -> !thread.isDaemon())
                .filter(Thread::isAlive)
                .toList();
    }
}
