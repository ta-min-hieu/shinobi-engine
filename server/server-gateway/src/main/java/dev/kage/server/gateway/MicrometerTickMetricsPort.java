package dev.kage.server.gateway;

import dev.kage.server.world.TickMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Implementation thật của {@link TickMetricsPort} (ADR-015) — {@code kage.tick.duration}, metric
 * đầu tiên của Phase 9 (OBSERVABILITY.md §3.1: "metric duy nhất mà không có nó thì không biết
 * server có khoẻ không"). {@code Timer} tạo 1 LẦN lúc construct, {@link #recordTickDuration} chỉ
 * ghi số đo — không allocation, an toàn gọi mỗi tick trên tick thread (/CLAUDE.md §6).
 */
final class MicrometerTickMetricsPort implements TickMetricsPort {

    private final Timer tickTimer;

    /**
     * {@code worldTag} là nhãn CỐ ĐỊNH ("main"/"dungeon"/"arena"), KHÔNG phải ID instance —
     * OBSERVABILITY.md §2 "không dùng ID làm tag metric" (cardinality nổ). Percentile publish
     * (p50/p95/p99) tính phía client (Micrometer), không cần Prometheus histogram_quantile ở MVP.
     */
    MicrometerTickMetricsPort(MeterRegistry registry, String worldTag) {
        this.tickTimer = Timer.builder("kage.tick.duration")
                .description("Thời gian 1 World tick chạy xong (WorldFacade.tickOnce)")
                .tag("world", worldTag)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    @Override
    public void recordTickDuration(long durationNanos) {
        tickTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
