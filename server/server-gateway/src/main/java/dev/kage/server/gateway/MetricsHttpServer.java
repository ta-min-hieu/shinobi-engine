package dev.kage.server.gateway;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Endpoint {@code GET /metrics} cho Prometheus pull (TECHNOLOGY-STACK.md §7: "Pull model hợp game
 * server") — ADR-015, Phase 9 lát cắt 1. Dùng {@code com.sun.net.httpserver} (JDK có sẵn, module
 * {@code jdk.httpserver}) thay vì dựng pipeline Netty HTTP riêng — endpoint scrape đơn giản, không
 * đáng thêm codec HTTP vào {@code server-gateway} chỉ vì việc này (Điều 9).
 *
 * <p>Chạy trên thread riêng của chính nó (JDK {@code HttpServer} tự quản lý), KHÔNG đụng world
 * tick thread hay Netty EventLoop — an toàn theo "Ba ranh giới thread" (server/CLAUDE.md).
 */
final class MetricsHttpServer {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsHttpServer.class);
    private static final String METRICS_PATH = "/metrics";

    private final HttpServer httpServer;

    MetricsHttpServer(int port, PrometheusMeterRegistry registry) {
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
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    void start() {
        httpServer.start();
        LOG.info("Metrics HTTP server sẵn sàng ở cổng {}{}", httpServer.getAddress().getPort(), METRICS_PATH);
    }

    /** Graceful shutdown (SERVER.md §10) — {@code delaySeconds=0} vì scrape không có state cần lưu. */
    void stop() {
        httpServer.stop(0);
    }
}
