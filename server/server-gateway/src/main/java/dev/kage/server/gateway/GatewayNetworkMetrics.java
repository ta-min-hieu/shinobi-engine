package dev.kage.server.gateway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import dev.kage.protocol.Opcode;

/**
 * OBSERVABILITY.md §3.2 (Network) + §3.5 (Business, phần {@code kage.login.*}/{@code
 * kage.item.enhance}) — 1 instance DÙNG CHUNG cho MỌI kết nối (khác {@link GatewayChannelHandler},
 * vốn {@code new} một lần MỖI kết nối), vì các số liệu này là tổng TOÀN SERVER. Counter theo
 * opcode/kết quả pre-build LẦN ĐẦU gặp qua {@link ConcurrentHashMap#computeIfAbsent} — nhiều
 * Netty EventLoop thread của nhiều kết nối khác nhau gọi đồng thời vào CÙNG instance này, cần
 * {@code ConcurrentHashMap} (cùng lý do {@code MainWorldChannelRegistry} phải sửa sang
 * {@code ConcurrentHashMap}, xem BENCHMARK.md/ROADMAP.md Phase 9 lát cắt 7). Tập giá trị tag bị
 * chặn ở enum {@code Opcode}/kết quả nghiệp vụ đã biết trước (~50-90 opcode, ~7 kết quả cường hoá)
 * — không phình cardinality (OBSERVABILITY.md §4).
 */
final class GatewayNetworkMetrics {

    private static final String UNKNOWN_OPCODE_TAG = "unknown";

    private final MeterRegistry registry;
    private final Map<String, Counter> packetsReceived = new ConcurrentHashMap<>();
    private final Map<String, Counter> packetsSent = new ConcurrentHashMap<>();
    private final Map<String, Counter> rateLimitRejected = new ConcurrentHashMap<>();
    private final Map<String, Counter> enhanceResults = new ConcurrentHashMap<>();
    private final Counter loginSuccess;
    private final Counter loginFailed;
    private final DistributionSummary packetSizeReceived;
    private final DistributionSummary packetSizeSent;
    private final AtomicInteger activeConnections = new AtomicInteger();

    GatewayNetworkMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.loginSuccess = Counter.builder("kage.login.success")
                .description("Đăng nhập thành công (OBSERVABILITY.md §3.5)")
                .register(registry);
        this.loginFailed = Counter.builder("kage.login.failed")
                .description("Đăng nhập thất bại — tăng vọt nghi tấn công brute-force")
                .register(registry);
        Gauge.builder("kage.connection.active", activeConnections, AtomicInteger::get)
                .description("Số kết nối TCP đang mở — khác kage.players.online (đã chọn nhân vật)")
                .register(registry);
        this.packetSizeReceived = DistributionSummary.builder("kage.packet.size")
                .tag("direction", "received")
                .baseUnit("bytes")
                .description("Kích thước payload packet — phát hiện client gửi packet bất thường lớn")
                .register(registry);
        this.packetSizeSent = DistributionSummary.builder("kage.packet.size")
                .tag("direction", "sent")
                .baseUnit("bytes")
                .description("Kích thước payload packet gửi đi — phát hiện response phình bất thường")
                .register(registry);
    }

    void recordPacketReceived(int opcode, int payloadSizeBytes) {
        counterFor(packetsReceived, "kage.packet.received", opcodeName(opcode)).increment();
        packetSizeReceived.record(payloadSizeBytes);
    }

    void recordPacketSent(int opcode, int payloadSizeBytes) {
        counterFor(packetsSent, "kage.packet.sent", opcodeName(opcode)).increment();
        packetSizeSent.record(payloadSizeBytes);
    }

    /** {@code scope} = nhãn rate limiter CỐ ĐỊNH ("move", tên {@code ChatChannel}...), KHÔNG phải characterId. */
    void recordRateLimitRejected(String scope) {
        counterFor(rateLimitRejected, "kage.ratelimit.rejected", "scope", scope).increment();
    }

    void recordLoginSuccess() {
        loginSuccess.increment();
    }

    void recordLoginFailed() {
        loginFailed.increment();
    }

    /** {@code result} = tên {@code InventoryFacade.EnhanceResult} (~7 giá trị cố định). */
    void recordEnhanceResult(String result) {
        counterFor(enhanceResults, "kage.item.enhance", "result", result).increment();
    }

    void connectionOpened() {
        activeConnections.incrementAndGet();
    }

    void connectionClosed() {
        activeConnections.decrementAndGet();
    }

    /** Tên hằng {@code Opcode} thật, hoặc {@value #UNKNOWN_OPCODE_TAG} nếu số hiệu lạ (packet giả mạo). */
    private static String opcodeName(int opcode) {
        Opcode value = Opcode.forNumber(opcode);
        return value != null ? value.name() : UNKNOWN_OPCODE_TAG;
    }

    private Counter counterFor(Map<String, Counter> cache, String metricName, String tagValue) {
        return counterFor(cache, metricName, "opcode", tagValue);
    }

    private Counter counterFor(Map<String, Counter> cache, String metricName, String tagKey, String tagValue) {
        return cache.computeIfAbsent(
                tagValue,
                value -> Counter.builder(metricName).tag(tagKey, value).register(registry));
    }
}
