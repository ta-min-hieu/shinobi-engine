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
    private static final Pattern QUANTILE_LINE =
            Pattern.compile("kage_tick_duration_seconds\\{[^}]*quantile=\"(0\\.\\d+)\"[^}]*}\\s+([0-9.eE+-]+)");

    // kage.players.online (GatewayBootstrap) là Gauge KHÔNG có tag → Prometheus in ra không kèm
    // {...} (vd "kage_players_online 199.0"). 2 nhánh tách rời (có nhãn / không nhãn) thay vì bọc
    // "{...}" trong 1 group rồi quantify thêm (?) — hình dạng đó bị SpotBugs/find-sec-bugs (SECRD)
    // báo nhầm ReDoS, và quan trọng hơn: [^}]* không bị chặn biên nếu KHÔNG có "{" sẽ tham lam ăn
    // qua nhiều dòng phía sau tới tận dấu "}" tiếp theo trong toàn file — sai kết quả. 2 nhánh rõ
    // ràng ở đây luôn bị chặn biên đúng (có "{...}" thật hoặc không có gì cả), không tham lam.
    private static final Pattern PLAYERS_ONLINE_LINE =
            Pattern.compile("kage_players_online\\{[^}]*}\\s+([0-9.eE+-]+)" + "|kage_players_online\\s+([0-9.eE+-]+)");

    // kage.gold.total (Gauge, cũng KHÔNG có tag — GatewayBootstrap) scrape ra tên kage_gold (không
    // phải kage_gold_total — Micrometer strip hậu tố ".total" cho Gauge, OBSERVABILITY.md §8, PHÁT
    // HIỆN THẬT xác nhận bằng dump registry.scrape() lúc viết lát cắt baseline 2026-08-02). Cùng
    // hình dạng 2-nhánh với PLAYERS_ONLINE_LINE ở trên, cùng lý do (né ReDoS + tham lam biên).
    private static final Pattern GOLD_TOTAL_LINE =
            Pattern.compile("kage_gold\\{[^}]*}\\s+([0-9.eE+-]+)" + "|kage_gold\\s+([0-9.eE+-]+)");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final URI metricsUri;

    MetricsScraper(String host, int metricsPort) {
        this.metricsUri = URI.create("http://" + host + ":" + metricsPort + "/metrics");
    }

    /**
     * Ảnh chụp 1 lần {@code GET /metrics}: latency tick phía server (percentile) VÀ
     * {@code kage_players_online} tại đúng thời điểm scrape đó — 2 con số đến từ CÙNG 1 response
     * body nên luôn đồng bộ thời gian với nhau (BENCHMARK.md §5.1, tránh lệch pha giữa 2 lần gọi
     * HTTP riêng).
     */
    record MetricsSnapshot(
            double p50Millis, double p95Millis, double p99Millis, double playersOnline, double goldTotal) {
        static final MetricsSnapshot UNAVAILABLE = new MetricsSnapshot(-1, -1, -1, -1, -1);
    }

    MetricsSnapshot scrape() {
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
            return MetricsSnapshot.UNAVAILABLE;
        }
    }

    static MetricsSnapshot parse(String body) {
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
        double playersOnline = -1;
        Matcher playersMatcher = PLAYERS_ONLINE_LINE.matcher(body);
        if (playersMatcher.find()) {
            String value = playersMatcher.group(1) != null ? playersMatcher.group(1) : playersMatcher.group(2);
            playersOnline = Double.parseDouble(value);
        }
        double goldTotal = -1;
        Matcher goldMatcher = GOLD_TOTAL_LINE.matcher(body);
        if (goldMatcher.find()) {
            String value = goldMatcher.group(1) != null ? goldMatcher.group(1) : goldMatcher.group(2);
            goldTotal = Double.parseDouble(value);
        }
        return new MetricsSnapshot(p50, p95, p99, playersOnline, goldTotal);
    }
}
