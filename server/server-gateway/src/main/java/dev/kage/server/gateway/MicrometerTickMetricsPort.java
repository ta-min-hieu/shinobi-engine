package dev.kage.server.gateway;

import dev.kage.server.world.TickMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation thật của {@link TickMetricsPort} (ADR-015) — {@code kage.tick.duration}, metric
 * đầu tiên của Phase 9 (OBSERVABILITY.md §3.1: "metric duy nhất mà không có nó thì không biết
 * server có khoẻ không") + {@code kage.tick.overrun.count} (Phase 9 lát cắt 8) + {@code
 * kage.entities.count}/{@code kage.ecs.system.duration} (Phase 9 lát cắt 9). Mọi {@code Timer}/
 * {@code Counter}/{@code Gauge} tạo 1 LẦN lúc construct, các phương thức {@code record*} chỉ ghi
 * số đo — không allocation, an toàn gọi mỗi tick trên tick thread (/CLAUDE.md §6).
 */
final class MicrometerTickMetricsPort implements TickMetricsPort {

    /**
     * Tên System CỐ ĐỊNH — khớp {@code getClass().getSimpleName()} của mọi
     * {@code IteratingKSystem} mà {@code WorldFacade} constructor đăng ký (kể cả
     * {@code MovementSystem} của engine-ecs). Biết trước danh sách này cho phép pre-build Timer
     * cho từng tên NGAY lúc construct — không tạo Timer mới trên tick thread nếu tên hợp lệ.
     */
    private static final String[] SYSTEM_NAMES = {
        "AiSystem", "MovementSystem", "CombatSystem", "SkillSystem", "BossAiSystem", "StatusEffectSystem"
    };

    private final Timer tickTimer;
    private final Counter tickOverrunCounter;
    private final Map<String, Timer> systemTimers;
    private final AtomicInteger entityCount = new AtomicInteger();
    private final MeterRegistry registry;
    private final String worldTag;

    /**
     * {@code worldTag} là nhãn CỐ ĐỊNH ("main"/"dungeon"/"arena"), KHÔNG phải ID instance —
     * OBSERVABILITY.md §2 "không dùng ID làm tag metric" (cardinality nổ). Percentile publish
     * (p50/p95/p99) tính phía client (Micrometer), không cần Prometheus histogram_quantile ở MVP.
     */
    MicrometerTickMetricsPort(MeterRegistry registry, String worldTag) {
        this.registry = registry;
        this.worldTag = worldTag;
        this.tickTimer = Timer.builder("kage.tick.duration")
                .description("Thời gian 1 World tick chạy xong (WorldFacade.tickOnce)")
                .tag("world", worldTag)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.tickOverrunCounter = Counter.builder("kage.tick.overrun.count")
                .description("Số lần 1 tick vượt ngân sách FIXED_DT (SERVER.md §3)")
                .tag("world", worldTag)
                .register(registry);
        this.systemTimers = buildSystemTimers();
        Gauge.builder("kage.entities.count", entityCount, AtomicInteger::get)
                .description("Tổng entity ECS đang sống (nhân vật + quái + NPC)")
                .tag("world", worldTag)
                .register(registry);
    }

    private Map<String, Timer> buildSystemTimers() {
        Map<String, Timer> timers = new HashMap<>();
        for (String systemName : SYSTEM_NAMES) {
            timers.put(systemName, Timer.builder("kage.ecs.system.duration")
                    .description("Thời gian 1 System xử lý xong toàn bộ entity của nó trong 1 tick")
                    .tag("world", worldTag)
                    .tag("system", systemName)
                    .register(registry));
        }
        return timers;
    }

    @Override
    public void recordTickDuration(long durationNanos) {
        tickTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordTickOverrun() {
        tickOverrunCounter.increment();
    }

    @Override
    public void recordSystemDuration(String systemName, long durationNanos) {
        Timer timer = systemTimers.get(systemName);
        if (timer == null) {
            // Không nên xảy ra (SYSTEM_NAMES phải khớp WorldFacade) — fallback an toàn thay vì
            // NullPointerException làm chết tick thread; log 1 lần thay vì mỗi tick sẽ cần thêm
            // state, chưa cần thiết cho lát cắt này (chưa ai gặp nhánh này trong test).
            return;
        }
        timer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordEntityCount(int count) {
        entityCount.set(count);
    }
}
