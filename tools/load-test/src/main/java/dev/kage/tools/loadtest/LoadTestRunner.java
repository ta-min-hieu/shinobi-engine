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

        SimulatedPlayer.Metrics metrics = new SimulatedPlayer.Metrics(
                new LatencyRecorder(), new AtomicLong(), new AtomicLong(), new AtomicLong());
        MetricsScraper metricsScraper = new MetricsScraper(config.host(), config.metricsPort());
        List<SimulatedPlayer> players = new CopyOnWriteArrayList<>();
        AtomicInteger nextClientIndex = new AtomicInteger();
        ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();

        Runnable spawnOneClient = () -> {
            SimulatedPlayer player = new SimulatedPlayer(
                    config.host(), config.port(), "loadtest" + nextClientIndex.getAndIncrement(), metrics);
            players.add(player);
            clientExecutor.submit(player);
        };
        for (int i = 0; i < config.initialClients(); i++) {
            spawnOneClient.run();
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        ScheduledFuture<?> rampTask = scheduleRamp(scheduler, config, players, spawnOneClient);
        scheduler.scheduleAtFixedRate(
                () -> report(players.size(), metrics, metricsScraper, config.reportIntervalSeconds()),
                config.reportIntervalSeconds(),
                config.reportIntervalSeconds(),
                TimeUnit.SECONDS);

        RunningTest runningTest = new RunningTest(rampTask, scheduler, players, clientExecutor);
        AtomicBoolean stopped = new AtomicBoolean();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopAll(stopped, runningTest), "load-test-shutdown"));

        Thread.sleep(Duration.ofSeconds(config.durationSeconds()));
        stopAll(stopped, runningTest);
        report(0, metrics, metricsScraper, config.reportIntervalSeconds());
        LOG.info("Load test kết thúc.");
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
                    LOG.info("Ramp: {} client đang hoạt động", players.size());
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

    private static void report(
            int activeClients, SimulatedPlayer.Metrics metrics, MetricsScraper metricsScraper, long intervalSeconds) {
        LatencyRecorder.Snapshot latency = metrics.latencyRecorder().snapshot();
        MetricsScraper.TickSnapshot tick = metricsScraper.scrape();
        // getAndSet(0) — băng thông là RATE của khoảng vừa qua, không phải số cộng dồn từ đầu test.
        double sentKBs = metrics.bytesSent().getAndSet(0) / 1024.0 / intervalSeconds;
        double receivedKBs = metrics.bytesReceived().getAndSet(0) / 1024.0 / intervalSeconds;
        LOG.info(
                "[report] client={} packetLatencyMs(p50/p95/p99)={}/{}/{} (n={}) "
                        + "serverTickMs(p50/p95/p99)={}/{}/{} băngThôngKBs(gửi/nhận)={}/{} lỗi={}",
                activeClients,
                round(latency.p50Millis()),
                round(latency.p95Millis()),
                round(latency.p99Millis()),
                latency.count(),
                round(tick.p50Millis()),
                round(tick.p95Millis()),
                round(tick.p99Millis()),
                round(sentKBs),
                round(receivedKBs),
                metrics.errorCount().get());
    }

    private static double round(double millis) {
        return Math.round(millis * 100) / 100.0;
    }
}
