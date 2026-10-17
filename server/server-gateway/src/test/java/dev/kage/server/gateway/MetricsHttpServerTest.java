package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Test end-to-end qua HTTP thật (không mock) — đúng tinh thần {@code GatewayServerIntegrationTest}
 * dùng socket thật cho protocol game. Xác nhận {@code kage.tick.duration} ghi qua
 * {@link MicrometerTickMetricsPort} thật sự xuất hiện ở endpoint {@code /metrics} theo đúng format
 * Prometheus text (ADR-015, Phase 9 lát cắt 1).
 */
class MetricsHttpServerTest {

    private MetricsHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void shouldExposeTickDurationMetric_afterRecordingViaPort() throws IOException, InterruptedException {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerTickMetricsPort port = new MicrometerTickMetricsPort(registry, "main");
        port.recordTickDuration(5_000_000L); // 5ms — giá trị bất kỳ, chỉ cần khác 0 để xuất hiện.

        int freePort = findFreePort();
        server = new MetricsHttpServer(freePort, registry, () -> true);
        server.start();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + freePort + "/metrics"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("kage_tick_duration_seconds").contains("world=\"main\"");
    }

    /**
     * OBSERVABILITY.md §3.3 {@code kage.db.query.duration}/{@code kage.db.transaction.failed} —
     * xác nhận bằng scrape HTTP thật (không đoán tên metric): dump cho thấy Timer ra tên
     * {@code kage_db_query_duration_seconds} (tag {@code operation}, KHÔNG bị hậu tố {@code .total}
     * strip như {@code kage.gold.total} → {@code kage_gold}, vì đây là Timer chứ không phải Gauge —
     * xem OBSERVABILITY.md §8 phát hiện cũ) và Counter ra tên
     * {@code kage_db_transaction_failed_total} (hậu tố {@code _total} CÓ được thêm cho Counter).
     */
    @Test
    void shouldExposeDbMetrics_afterRecordingViaPort() throws IOException, InterruptedException {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerDbMetricsPort port = new MicrometerDbMetricsPort(registry);
        port.recordQueryDuration("SELECT", 2_000_000L); // 2ms — giá trị bất kỳ, chỉ cần khác 0.
        port.recordTransactionFailed();

        int freePort = findFreePort();
        server = new MetricsHttpServer(freePort, registry, () -> true);
        server.start();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + freePort + "/metrics"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("kage_db_query_duration_seconds")
                .contains("operation=\"SELECT\"")
                .contains("kage_db_transaction_failed_total");
    }

    @Test
    void shouldReturnOk_forHealthLive_regardlessOfReadiness() throws IOException, InterruptedException {
        int freePort = findFreePort();
        server = new MetricsHttpServer(freePort, new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), () -> false);
        server.start();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + freePort + "/health/live"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void shouldReturn200ForHealthReady_whenReadinessCheckTrue() throws IOException, InterruptedException {
        int freePort = findFreePort();
        server = new MetricsHttpServer(freePort, new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), () -> true);
        server.start();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + freePort + "/health/ready"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void shouldReturn503ForHealthReady_whenReadinessCheckFalse() throws IOException, InterruptedException {
        int freePort = findFreePort();
        server = new MetricsHttpServer(freePort, new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), () -> false);
        server.start();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + freePort + "/health/ready"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(503);
    }

    /**
     * Vòng đời: {@code HttpServer.stop} không đóng executor do người gọi cấp qua
     * {@code setExecutor} — trước bản vá, executor virtual-thread sống tiếp sau {@code stop()}.
     */
    @Test
    void shouldShutDownExecutor_whenStopped() throws IOException {
        int freePort = findFreePort();
        MetricsHttpServer stopped =
                new MetricsHttpServer(freePort, new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), () -> true);
        stopped.start();
        assertThat(stopped.isExecutorShutdown()).isFalse();

        stopped.stop();

        assertThat(stopped.isExecutorShutdown()).isTrue();
    }

    static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
