package dev.kage.tools.loadtest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Đọc {@code kage.tick.duration} thật từ {@code GET /metrics} (Phase 9 lát cắt 1,
 * {@code MicrometerTickMetricsPort}) — cho phép report của load test hiển thị SONG SONG latency
 * phía client VÀ tick time phía server thật, thay vì chỉ đoán server có khoẻ không qua latency
 * gián tiếp.
 */
final class MetricsScraper {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsScraper.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    // Format Prometheus text của Micrometer Timer.publishPercentiles: mỗi quantile 1 dòng riêng,
    // nhãn khác (world=...) không quan trọng thứ tự — [^}]* bắt bất kỳ nhãn nào đứng trước/sau.
    private static final Pattern QUANTILE_LINE = Pattern.compile(
            "kage_tick_duration_seconds\\{[^}]*quantile=\"(0\\.\\d+)\"[^}]*}\\s+([0-9.eE+-]+)");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final URI metricsUri;

    MetricsScraper(String host, int metricsPort) {
        this.metricsUri = URI.create("http://" + host + ":" + metricsPort + "/metrics");
    }

    record TickSnapshot(double p50Millis, double p95Millis, double p99Millis) {
        static final TickSnapshot UNAVAILABLE = new TickSnapshot(-1, -1, -1);
    }

    TickSnapshot scrape() {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(metricsUri).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return parse(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("Không scrape được {} — server có đang chạy không?", metricsUri, e);
            return TickSnapshot.UNAVAILABLE;
        }
    }

    private static TickSnapshot parse(String body) {
        double p50 = -1;
        double p95 = -1;
        double p99 = -1;
        Matcher matcher = QUANTILE_LINE.matcher(body);
        while (matcher.find()) {
            double quantile = Double.parseDouble(matcher.group(1));
            double valueMillis = Double.parseDouble(matcher.group(2)) * 1000.0;
            if (quantile == 0.5) {
                p50 = valueMillis;
            } else if (quantile == 0.95) {
                p95 = valueMillis;
            } else if (quantile == 0.99) {
                p99 = valueMillis;
            }
        }
        return new TickSnapshot(p50, p95, p99);
    }
}
