package dev.kage.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * BENCHMARK.md §5.1 — lần đo N=2000 hỏng vì chỉ có 1 snapshot post-hoc của
 * {@code kage_players_online}, chụp trễ sau khi client đã ngắt hết. Test này verify: (1)
 * {@code kage_players_online} parse đúng từ CÙNG response body với {@code kage_tick_duration_seconds},
 * (2) đỉnh tải (running-max) được giữ đúng qua nhiều lần scrape thay vì chỉ đọc số cuối cùng.
 */
final class MetricsScraperTest {

    private static final String SAMPLE_BODY = """
            # HELP kage_tick_duration_seconds Tick duration
            # TYPE kage_tick_duration_seconds summary
            kage_tick_duration_seconds{world="main",quantile="0.5"} 0.002
            kage_tick_duration_seconds{world="main",quantile="0.95"} 0.006
            kage_tick_duration_seconds{world="main",quantile="0.99"} 0.009
            # HELP kage_players_online Số người chơi đang online
            # TYPE kage_players_online gauge
            kage_players_online 199.0
            # HELP kage_gold Tổng gold toàn server
            # TYPE kage_gold gauge
            kage_gold 12345.0
            """;

    @Test
    void shouldParsePlayersOnline_whenBodyHasNoLabels() {
        MetricsScraper.MetricsSnapshot snapshot = MetricsScraper.parse(SAMPLE_BODY);

        assertThat(snapshot.playersOnline()).isEqualTo(199.0);
    }

    @Test
    void shouldParseTickQuantiles_fromSameBodyAsPlayersOnline() {
        MetricsScraper.MetricsSnapshot snapshot = MetricsScraper.parse(SAMPLE_BODY);

        assertThat(snapshot.p50Millis()).isEqualTo(2.0);
        assertThat(snapshot.p95Millis()).isEqualTo(6.0);
        assertThat(snapshot.p99Millis()).isEqualTo(9.0);
    }

    @Test
    void shouldReportUnavailable_whenPlayersOnlineLineMissing() {
        String bodyWithoutGauge = """
                kage_tick_duration_seconds{world="main",quantile="0.5"} 0.002
                """;

        MetricsScraper.MetricsSnapshot snapshot = MetricsScraper.parse(bodyWithoutGauge);

        assertThat(snapshot.playersOnline()).isEqualTo(-1);
    }

    /**
     * {@code kage.tick.duration} mang tag {@code world} = main/dungeon/arena
     * ({@code MicrometerTickMetricsPort}). Bản cũ lấy dòng quantile NÀO đọc sau cùng — một khi đã
     * có hầm ngục/đấu trường từng mở (meter không bị gỡ khi đóng), report hiện tick của khu đó, mà
     * percentile của một timer không còn ghi gì thì tự rơi về 0 ⇒ "tick p99 = 0" giả.
     */
    @Test
    void shouldReadMainWorldTick_whenOtherWorldSeriesFollow() {
        String body = """
                kage_tick_duration_seconds{quantile="0.5",world="main",} 0.002
                kage_tick_duration_seconds{quantile="0.95",world="main",} 0.006
                kage_tick_duration_seconds{quantile="0.99",world="main",} 0.009
                kage_tick_duration_seconds{quantile="0.5",world="dungeon",} 0.0
                kage_tick_duration_seconds{quantile="0.95",world="dungeon",} 0.0
                kage_tick_duration_seconds{quantile="0.99",world="dungeon",} 0.0
                """;

        MetricsScraper.MetricsSnapshot snapshot = MetricsScraper.parse(body);

        assertThat(snapshot.p50Millis()).isEqualTo(2.0);
        assertThat(snapshot.p95Millis()).isEqualTo(6.0);
        assertThat(snapshot.p99Millis()).isEqualTo(9.0);
    }

    @Test
    void shouldParseLabeledPlayersOnlineLine_whenGaugeHasTags() {
        String bodyWithLabel = """
                kage_players_online{world="0"} 42.0
                """;

        MetricsScraper.MetricsSnapshot snapshot = MetricsScraper.parse(bodyWithLabel);

        assertThat(snapshot.playersOnline()).isEqualTo(42.0);
    }

    // kage_gold (OBSERVABILITY.md §8.1 — nền tảng đo baseline GoldTotalSpike) parse ĐÚNG tên strip
    // ".total" của Micrometer cho Gauge — cùng hình dạng regex với players_online nên test lặp lại
    // ba nhánh tương ứng (không nhãn / thiếu dòng / có nhãn) thay vì chỉ 1 test smoke.
    @Test
    void shouldParseGoldTotal_fromSameBodyAsPlayersOnline() {
        MetricsScraper.MetricsSnapshot snapshot = MetricsScraper.parse(SAMPLE_BODY);

        assertThat(snapshot.goldTotal()).isEqualTo(12345.0);
    }

    @Test
    void shouldReportUnavailable_whenGoldTotalLineMissing() {
        String bodyWithoutGauge = """
                kage_tick_duration_seconds{world="main",quantile="0.5"} 0.002
                """;

        MetricsScraper.MetricsSnapshot snapshot = MetricsScraper.parse(bodyWithoutGauge);

        assertThat(snapshot.goldTotal()).isEqualTo(-1);
    }

    @Test
    void shouldParseLabeledGoldTotalLine_whenGaugeHasTags() {
        String bodyWithLabel = """
                kage_gold{world="0"} 777.0
                """;

        MetricsScraper.MetricsSnapshot snapshot = MetricsScraper.parse(bodyWithLabel);

        assertThat(snapshot.goldTotal()).isEqualTo(777.0);
    }

    @Test
    void shouldKeepPeak_whenLaterScrapesAreLower() {
        AtomicInteger peak = new AtomicInteger();

        LoadTestRunner.trackPeak(new MetricsScraper.MetricsSnapshot(-1, -1, -1, 50, -1), peak);
        LoadTestRunner.trackPeak(new MetricsScraper.MetricsSnapshot(-1, -1, -1, 199, -1), peak);
        LoadTestRunner.trackPeak(new MetricsScraper.MetricsSnapshot(-1, -1, -1, 120, -1), peak);

        assertThat(peak.get()).isEqualTo(199);
    }

    @Test
    void shouldIgnoreUnavailableSnapshot_whenScrapeFailed() {
        AtomicInteger peak = new AtomicInteger();

        LoadTestRunner.trackPeak(new MetricsScraper.MetricsSnapshot(-1, -1, -1, 80, -1), peak);
        LoadTestRunner.trackPeak(MetricsScraper.MetricsSnapshot.UNAVAILABLE, peak);

        assertThat(peak.get()).isEqualTo(80);
    }
}
