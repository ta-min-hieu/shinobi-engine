package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * Dòng 62(i) (2026-10-17) — {@code /health/ready} đòi cả cổng game còn phục vụ được, không chỉ DB.
 */
class GatewayReadinessTest {

    @Test
    void gameTrafficCheckShouldFollowTheServerLifecycle() throws Exception {
        GatewayServer server = new GatewayServer(0, null, null, TlsConfig.disabled());
        assertThat(server.isServingGameTraffic()).as("chưa mở cổng").isFalse();

        server.start();
        try {
            assertThat(server.isServingGameTraffic())
                    .as("cổng mở, event loop chạy")
                    .isTrue();
        } finally {
            server.stop();
        }

        assertThat(server.isServingGameTraffic()).as("đã tắt").isFalse();
    }

    @Test
    void readyShouldBe503_whenAnAddedCheckFails() throws IOException, InterruptedException {
        int port = MetricsHttpServerTest.findFreePort();
        MetricsHttpServer metrics =
                new MetricsHttpServer(port, new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), () -> true);
        metrics.addReadinessCheck(() -> false);
        metrics.start();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(
                            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health/ready"))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(503);
        } finally {
            metrics.stop();
        }
    }
}
