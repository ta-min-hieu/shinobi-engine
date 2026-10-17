package dev.kage.server.gateway;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trần theo ĐỊA CHỈ IP: số kết nối mở cùng lúc và số lần đăng nhập mỗi phút (dòng 71(a), chủ dự án
 * duyệt 2026-10-17).
 *
 * <p><b>Vì sao cần.</b> Đăng nhập chỉ bằng username và tự tạo tài khoản nếu chưa có
 * ({@code LoginOrCreateAccountUseCase}), nên mọi trần theo tài khoản — chat, giao dịch, lời mời —
 * vượt được bằng cách đổi sang một username mới. Trước lớp này gateway không có trần nào mà một
 * username mới không làm trắng lại: một máy đẻ được hàng nghìn tài khoản mỗi phút và mở bao nhiêu
 * kết nối cũng được.
 *
 * <p><b>Số chọn rộng tay.</b> Nhiều người chơi thật có thể chung một IP (quán net, mạng gia đình,
 * NAT của nhà mạng) — trần này để chặn MÁY bơm, không để phân xử người chơi. 16 kết nối cùng lúc đủ
 * cho một phòng máy nhỏ; 10 lần đăng nhập mỗi phút là ~1 lần mỗi 6 giây cho cả IP, thừa cho người
 * thật đăng nhập lại sau khi rớt mạng, nhưng hạ một máy tạo tài khoản từ không giới hạn xuống 600
 * tài khoản mỗi giờ. Cả hai đổi được bằng biến môi trường (xem {@link #fromEnvironment}); giá trị
 * {@code 0} là tắt hẳn trần đó.
 *
 * <p><b>Loopback miễn trừ.</b> Load test (N=2000 từ một máy, BENCHMARK §5) và {@code gameProbe} đều
 * chạy từ {@code 127.0.0.1}. ⚠️ Sau một proxy ngược hoặc userland-proxy của Docker, mọi kết nối có
 * thể mang CÙNG một IP nguồn — khi đó đặt hai biến môi trường về 0, nếu không cả server chung một trần.
 *
 * <p><b>Luồng.</b> {@link #tryOpen}/{@link #close} chạy trên EventLoop của từng kết nối,
 * {@link #tryLogin} trên Virtual Thread đăng nhập — mọi trạng thái nằm trong map đồng thời, mỗi sổ
 * một IP tự khoá. Sổ đăng nhập im lặng quá hai cửa sổ thì bị quên (cùng lý do {@link ChatRateLimits}).
 */
final class IpAdmission {

    private static final Logger LOG = LoggerFactory.getLogger(IpAdmission.class);

    static final int DEFAULT_MAX_CONNECTIONS_PER_IP = 16;
    static final int DEFAULT_LOGINS_PER_MINUTE = 10;
    static final String MAX_CONNECTIONS_ENV = "KAGE_MAX_CONNECTIONS_PER_IP";
    static final String LOGINS_PER_MINUTE_ENV = "KAGE_LOGINS_PER_IP_PER_MINUTE";

    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

    private final int maxConnectionsPerIp;
    private final int loginsPerWindow;
    private final LongSupplier nanoTimeSource;
    private final Map<InetAddress, Integer> openConnections = new ConcurrentHashMap<>();
    private final Map<InetAddress, RateLimiter> loginLimiters = new ConcurrentHashMap<>();
    private final Map<InetAddress, Long> lastLoginNanos = new ConcurrentHashMap<>();
    private volatile long lastSweepNanos;

    IpAdmission(int maxConnectionsPerIp, int loginsPerWindow) {
        this(maxConnectionsPerIp, loginsPerWindow, System::nanoTime);
    }

    /** Constructor cho test — {@code nanoTimeSource} giả để không phụ thuộc thời gian thật trôi. */
    IpAdmission(int maxConnectionsPerIp, int loginsPerWindow, LongSupplier nanoTimeSource) {
        if (maxConnectionsPerIp < 0 || loginsPerWindow < 0) {
            throw new IllegalArgumentException(
                    "trần IP không được âm: " + maxConnectionsPerIp + ", " + loginsPerWindow);
        }
        this.maxConnectionsPerIp = maxConnectionsPerIp;
        this.loginsPerWindow = loginsPerWindow;
        this.nanoTimeSource = nanoTimeSource;
        this.lastSweepNanos = nanoTimeSource.getAsLong();
    }

    /** Trần đọc từ biến môi trường, thiếu thì dùng mặc định; giá trị hỏng thì mặc định kèm WARN. */
    static IpAdmission fromEnvironment() {
        return new IpAdmission(
                readLimit(MAX_CONNECTIONS_ENV, DEFAULT_MAX_CONNECTIONS_PER_IP),
                readLimit(LOGINS_PER_MINUTE_ENV, DEFAULT_LOGINS_PER_MINUTE));
    }

    /** Không trần nào — cho test không quan tâm tới IP. */
    static IpAdmission unlimited() {
        return new IpAdmission(0, 0);
    }

    private static int readLimit(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            LOG.warn("{}={} không phải số nguyên — dùng mặc định {}", name, raw, fallback, e);
            return fallback;
        }
        LOG.warn("{}={} âm — dùng mặc định {}", name, raw, fallback);
        return fallback;
    }

    /**
     * Một kết nối vừa mở. {@code false} nghĩa là IP đã đủ trần — caller PHẢI đóng kết nối, và KHÔNG
     * được gọi {@link #close} cho nó (kết nối bị từ chối chưa từng được đếm).
     */
    boolean tryOpen(SocketAddress remote) {
        Optional<InetAddress> address = limitedAddress(remote);
        if (address.isEmpty() || maxConnectionsPerIp == 0) {
            return true;
        }
        boolean[] admitted = {false};
        openConnections.compute(address.get(), (ip, count) -> {
            int current = count == null ? 0 : count;
            if (current >= maxConnectionsPerIp) {
                return count;
            }
            admitted[0] = true;
            return current + 1;
        });
        return admitted[0];
    }

    /** Một kết nối đã được {@link #tryOpen} nhận vừa đóng. */
    void close(SocketAddress remote) {
        Optional<InetAddress> address = limitedAddress(remote);
        if (address.isEmpty() || maxConnectionsPerIp == 0) {
            return;
        }
        openConnections.computeIfPresent(address.get(), (ip, count) -> count <= 1 ? null : count - 1);
    }

    /** Đếm một lần đăng nhập; {@code false} nghĩa là IP đã hết lượt trong phút này — caller từ chối. */
    boolean tryLogin(SocketAddress remote) {
        Optional<InetAddress> address = limitedAddress(remote);
        if (address.isEmpty() || loginsPerWindow == 0) {
            return true;
        }
        long now = nanoTimeSource.getAsLong();
        forgetIdleIfDue(now);
        InetAddress ip = address.get();
        lastLoginNanos.put(ip, now);
        RateLimiter limiter = loginLimiters.computeIfAbsent(
                ip, key -> new RateLimiter(loginsPerWindow, LOGIN_WINDOW, nanoTimeSource));
        synchronized (limiter) {
            return limiter.tryAcquire();
        }
    }

    /** Số kết nối đang mở của một IP — cho test. */
    int openConnectionCount(InetAddress address) {
        return openConnections.getOrDefault(address, 0);
    }

    /** Số IP đang có sổ đăng nhập — cho test kiểm sổ cũ bị quên. */
    int trackedLoginAddressCount() {
        return loginLimiters.size();
    }

    private void forgetIdleIfDue(long now) {
        if (now - lastSweepNanos < SWEEP_INTERVAL.toNanos()) {
            return;
        }
        lastSweepNanos = now;
        long idleNanos = LOGIN_WINDOW.multipliedBy(2).toNanos();
        lastLoginNanos.entrySet().removeIf(entry -> {
            boolean idle = now - entry.getValue() > idleNanos;
            if (idle) {
                loginLimiters.remove(entry.getKey());
            }
            return idle;
        });
    }

    /** IP phải chịu trần, hoặc rỗng nếu không phải IP thật (loopback, kênh test không có địa chỉ). */
    private static Optional<InetAddress> limitedAddress(SocketAddress remote) {
        if (remote instanceof InetSocketAddress inet
                && inet.getAddress() != null
                && !inet.getAddress().isLoopbackAddress()) {
            return Optional.of(inet.getAddress());
        }
        return Optional.empty();
    }
}
