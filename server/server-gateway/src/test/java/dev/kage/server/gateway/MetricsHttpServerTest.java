package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.ServerSocket;
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
        server = new MetricsHttpServer(freePort, registry);
        server.start();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + freePort + "/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("kage_tick_duration_seconds")
                .contains("world=\"main\"");
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
