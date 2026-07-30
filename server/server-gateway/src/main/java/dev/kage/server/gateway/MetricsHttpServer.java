package dev.kage.server.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Endpoint {@code GET /metrics} cho Prometheus pull (TECHNOLOGY-STACK.md §7: "Pull model hợp game
 * server") — ADR-015, Phase 9 lát cắt 1 — + {@code GET /health/live}/{@code /health/ready}
 * (OBSERVABILITY.md §7, Phase 9 lát cắt 8). Dùng {@code com.sun.net.httpserver} (JDK có sẵn, module
 * {@code jdk.httpserver}) thay vì dựng pipeline Netty HTTP riêng — endpoint đơn giản, không đáng
 * thêm codec HTTP vào {@code server-gateway} chỉ vì việc này (Điều 9); cả 3 endpoint dùng CHUNG 1
 * {@code HttpServer} (Điều 9 — không dựng thêm 1 process/cổng chỉ để tách health check riêng).
 *
 * <p>Chạy trên thread riêng của chính nó (JDK {@code HttpServer} tự quản lý), KHÔNG đụng world
 * tick thread hay Netty EventLoop — an toàn theo "Ba ranh giới thread" (server/CLAUDE.md). Handler
 * {@code /health/ready} gọi {@code readinessCheck} (chạm DB) trên thread pool RIÊNG của
 * {@code HttpServer} này, không phải EventLoop/tick thread nên KHÔNG vi phạm ranh giới đó.
 */
final class MetricsHttpServer {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsHttpServer.class);
    private static final String METRICS_PATH = "/metrics";
    private static final String HEALTH_LIVE_PATH = "/health/live";
    private static final String HEALTH_READY_PATH = "/health/ready";

    private final HttpServer httpServer;

    /**
     * {@code readinessCheck} — OBSERVABILITY.md §7: "sẵn sàng nhận người chơi? (DB kết nối được?
     * script load xong?)". Script validation đã chạy fail-fast TRƯỚC lúc server nghe cổng
     * ({@code GatewayBootstrap.main}, ném exception nếu lỗi — process không tồn tại để trả
     * {@code /health/ready} nếu bước đó thất bại), nên chỉ còn DB cần kiểm tra LIVE ở đây.
     */
    MetricsHttpServer(int port, PrometheusMeterRegistry registry, BooleanSupplier readinessCheck) {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Không bind được cổng metrics " + port, e);
        }
        httpServer.createContext(METRICS_PATH, exchange -> {
            byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }
        });
        // /health/live — process còn sống? Handler này CHẠY ĐƯỢC tự nó đã là câu trả lời "có", không
        // cần kiểm tra phụ thuộc nào (OBSERVABILITY.md §7 — live KHÔNG được phụ thuộc DB, DB chết
        // không phải lý do restart process).
        httpServer.createContext(HEALTH_LIVE_PATH, exchange -> writePlainText(exchange, 200, "OK"));
        httpServer.createContext(HEALTH_READY_PATH, exchange -> {
            boolean ready = readinessCheck.getAsBoolean();
            writePlainText(exchange, ready ? 200 : 503, ready ? "READY" : "NOT_READY");
        });
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    private static void writePlainText(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }

    void start() {
        httpServer.start();
        LOG.info(
                "Metrics HTTP server sẵn sàng ở cổng {}{}",
                httpServer.getAddress().getPort(),
                METRICS_PATH);
    }

    /** Graceful shutdown (SERVER.md §10) — {@code delaySeconds=0} vì scrape không có state cần lưu. */
    void stop() {
        httpServer.stop(0);
    }
}
