package dev.kage.tools.loadtest;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint {@code main()} — dựng dần {@code initialClients} → {@code maxClients} rồi giữ tải
 * (BENCHMARK.md §5 "Tăng dần tải: 10 → 50 → 100 → 500... tìm điểm gãy" + "Soak test ≥ 2 giờ").
 * Mặc định chạy NHẮM VÀO server thật đang chạy sẵn (không tự khởi động
 * {@code GatewayBootstrap}) — load test phải đo server production-like, không phải server test
 * trong cùng process.
 *
 * <p>Chạy: {@code ./gradlew :tools:load-test:run --args="--maxClients=200 --durationSeconds=7200"}
 * (mặc định đã là soak 2h — xem {@link LoadTestConfig}).
 */
public final class LoadTestRunner {

    private static final Logger LOG = LoggerFactory.getLogger(LoadTestRunner.class);

    private LoadTestRunner() {}

    public static void main(String[] args) throws InterruptedException {
        LoadTestConfig config = LoadTestConfig.parse(args);
        LOG.info(
                "Bắt đầu load test: {}:{} — {} → {} client (bước {} mỗi {}s), chạy {}s",
                config.host(),
                config.port(),
                config.initialClients(),
                config.maxClients(),
                config.rampStepClients(),
                config.rampStepIntervalSeconds(),
                config.durationSeconds());

        ReportingContext reporting = new ReportingContext(
                new SimulatedPlayer.Metrics(
                        new LatencyRecorder(), new FailureCounters(), new AtomicLong(), new AtomicLong()),
                new MetricsScraper(config.host(), config.metricsPort()),
                new AtomicInteger(),
                new AtomicLong(System.nanoTime()));
        List<SimulatedPlayer> players = new CopyOnWriteArrayList<>();
        AtomicInteger nextClientIndex = new AtomicInteger();
        ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();

        Runnable spawnOneClient = () -> {
            SimulatedPlayer player = new SimulatedPlayer(
                    config.host(), config.port(), "loadtest" + nextClientIndex.getAndIncrement(), reporting.metrics());
            players.add(player);
            // execute, không submit: submit gói exception vào Future không ai đọc — một client chết
            // vì lỗi lập trình sẽ biến mất không dấu vết. execute để nó tới uncaught handler.
            clientExecutor.execute(player);
        };
        for (int i = 0; i < config.initialClients(); i++) {
            spawnOneClient.run();
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        ScheduledFuture<?> rampTask = scheduleRamp(scheduler, config, players, spawnOneClient);
        scheduleReporting(scheduler, config, players, reporting);

        RunningTest runningTest = new RunningTest(rampTask, scheduler, players, clientExecutor);
        AtomicBoolean stopped = new AtomicBoolean();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopAll(stopped, runningTest), "load-test-shutdown"));

        Thread.sleep(Duration.ofSeconds(config.durationSeconds()));
        stopAll(stopped, runningTest);
        reportFinal(reporting, players);
        LOG.info("Load test kết thúc.");
    }

    /** Gộp state cần cho lịch report định kỳ — Checkstyle ParameterNumber tối đa 4 tham số. */
    private record ReportingContext(
            SimulatedPlayer.Metrics metrics,
            MetricsScraper metricsScraper,
            AtomicInteger peakPlayersOnline,
            AtomicLong lastReportNanos) {}

    // FutureReturnValueIgnored: scrape() tự bắt lỗi I/O và trả UNAVAILABLE; lịch report chỉ dừng
    // khi có lỗi lập trình, và chẳng có ai chờ Future này để báo lỗi đó sớm hơn log.
    @SuppressWarnings("FutureReturnValueIgnored")
    private static void scheduleReporting(
            ScheduledExecutorService scheduler,
            LoadTestConfig config,
            List<SimulatedPlayer> players,
            ReportingContext reporting) {
        scheduler.scheduleAtFixedRate(
                () -> report(
                        ClientCounts.of(players),
                        reporting,
                        scrapeAndTrackPeak(reporting.metricsScraper(), reporting.peakPlayersOnline())),
                config.reportIntervalSeconds(),
                config.reportIntervalSeconds(),
                TimeUnit.SECONDS);
    }

    /**
     * Report cuối cùng, SAU KHI đã dừng hết client — kèm log đỉnh {@code kage_players_online} thật
     * đo được LIÊN TỤC suốt quá trình chạy (BENCHMARK.md §5.1: thay cho cách đo cũ — 1 snapshot
     * post-hoc bằng {@code jcmd VM.native_memory} sau khi test kết thúc, dễ lệch pha nếu test dừng
     * sớm hơn dự kiến và client đã ngắt hết trước lúc chụp).
     */
    private static void reportFinal(ReportingContext reporting, List<SimulatedPlayer> players) {
        MetricsScraper.MetricsSnapshot snapshot =
                scrapeAndTrackPeak(reporting.metricsScraper(), reporting.peakPlayersOnline());
        report(ClientCounts.of(players), reporting, snapshot);
        LOG.info(
                "Đỉnh tải thật đo được: kage_players_online cao nhất = {}",
                reporting.peakPlayersOnline().get());
    }

    /**
     * Scrape + cập nhật đỉnh {@code kage_players_online} — tái dùng đúng lịch scrape có sẵn của
     * {@code report()}, không thêm vòng lặp polling thứ 2.
     */
    private static MetricsScraper.MetricsSnapshot scrapeAndTrackPeak(
            MetricsScraper metricsScraper, AtomicInteger peakPlayersOnline) {
        MetricsScraper.MetricsSnapshot snapshot = metricsScraper.scrape();
        trackPeak(snapshot, peakPlayersOnline);
        return snapshot;
    }

    /** Package-private để test đơn vị chạy trực tiếp logic running-max, không cần HTTP thật. */
    static void trackPeak(MetricsScraper.MetricsSnapshot snapshot, AtomicInteger peakPlayersOnline) {
        if (snapshot.playersOnline() >= 0) {
            peakPlayersOnline.accumulateAndGet((int) Math.round(snapshot.playersOnline()), Math::max);
        }
    }

    /** Gộp toàn bộ tài nguyên cần dọn lúc shutdown — Checkstyle ParameterNumber tối đa 4 tham số. */
    private record RunningTest(
            ScheduledFuture<?> rampTask,
            ScheduledExecutorService scheduler,
            List<SimulatedPlayer> players,
            ExecutorService clientExecutor) {}

    private static ScheduledFuture<?> scheduleRamp(
            ScheduledExecutorService scheduler,
            LoadTestConfig config,
            List<SimulatedPlayer> players,
            Runnable spawnOneClient) {
        if (config.maxClients() <= config.initialClients()) {
            return null;
        }
        return scheduler.scheduleAtFixedRate(
                () -> {
                    for (int i = 0; i < config.rampStepClients() && players.size() < config.maxClients(); i++) {
                        spawnOneClient.run();
                    }
                    LOG.info("Ramp: đã dựng {} client", players.size());
                },
                config.rampStepIntervalSeconds(),
                config.rampStepIntervalSeconds(),
                TimeUnit.SECONDS);
    }

    /** {@code stopped} chặn double-shutdown (sleep hết hạn bình thường VÀ Ctrl+C cùng gọi tới). */
    private static void stopAll(AtomicBoolean stopped, RunningTest test) {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        LOG.info("Đang dừng {} client...", test.players().size());
        if (test.rampTask() != null) {
            test.rampTask().cancel(false);
        }
        test.scheduler().shutdownNow();
        test.players().forEach(SimulatedPlayer::stop);
        test.clientExecutor().shutdown();
        try {
            test.clientExecutor().awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Số client theo trạng thái THẬT ({@link SimulatedPlayer.Phase}). Bản cũ in {@code players.size()}
     * — mọi client từng được dựng, kể cả đã bắt tay hỏng, đã bỏ cuộc, hay đã chết — dưới nhãn
     * "client=", nên "N client" trong report không phải N kết nối đang tạo tải.
     */
    record ClientCounts(int started, int inWorld, int ended) {

        static ClientCounts of(List<SimulatedPlayer> players) {
            int inWorld = 0;
            int ended = 0;
            for (SimulatedPlayer player : players) {
                SimulatedPlayer.Phase phase = player.phase();
                if (phase == SimulatedPlayer.Phase.IN_WORLD) {
                    inWorld++;
                } else if (phase == SimulatedPlayer.Phase.ENDED) {
                    ended++;
                }
            }
            return new ClientCounts(players.size(), inWorld, ended);
        }
    }

    /**
     * KB/s trên ĐÚNG khoảng thời gian đã trôi qua. Bản cũ chia cho {@code reportIntervalSeconds} cố
     * định — report cuối (khoảng lẻ, thường ngắn hơn nhiều) bị chia cho cả một khoảng đầy ⇒ băng
     * thông của report cuối bị báo THẤP theo đúng tỉ lệ khoảng lẻ/khoảng đầy.
     */
    static double kibPerSecond(long bytes, long elapsedNanos) {
        if (elapsedNanos <= 0) {
            return 0;
        }
        return bytes / 1024.0 / (elapsedNanos / 1_000_000_000.0);
    }

    private static void report(ClientCounts clients, ReportingContext reporting, MetricsScraper.MetricsSnapshot tick) {
        SimulatedPlayer.Metrics metrics = reporting.metrics();
        long now = System.nanoTime();
        long elapsedNanos = now - reporting.lastReportNanos().getAndSet(now);
        LatencyRecorder.Snapshot latency = metrics.latencyRecorder().snapshot();
        // getAndSet(0) — băng thông là RATE của khoảng vừa qua, không phải số cộng dồn từ đầu test.
        double sentKBs = kibPerSecond(metrics.bytesSent().getAndSet(0), elapsedNanos);
        double receivedKBs = kibPerSecond(metrics.bytesReceived().getAndSet(0), elapsedNanos);
        LOG.info(
                "[report] client(trongWorld/đãDựng/đãKếtThúc)={}/{}/{} "
                        + "packetLatencyMs(p50/p95/p99)={}/{}/{} (n={}, cậnDướiDoHếtGiờ={}) "
                        + "serverTickMs(p50/p95/p99)={}/{}/{} băngThôngKBs(gửi/nhận)={}/{} "
                        + "lỗiCộngDồn[{}] kageGold={}",
                clients.inWorld(),
                clients.started(),
                clients.ended(),
                round(latency.p50Millis()),
                round(latency.p95Millis()),
                round(latency.p99Millis()),
                latency.count(),
                latency.censoredCount(),
                round(tick.p50Millis()),
                round(tick.p95Millis()),
                round(tick.p99Millis()),
                round(sentKBs),
                round(receivedKBs),
                metrics.failures(),
                tick.goldTotal());
    }

    private static double round(double millis) {
        return Math.round(millis * 100) / 100.0;
    }
}
