package dev.kage.engine.ecs;

import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.MovementSystem;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Micro-benchmark {@code EcsWorld.update} (BENCHMARK.md §4 "🔴 Cao nhất — kích hoạt Revisit
 * ADR-006") — ngân sách ECS.md §9: **≤ 4ms/tick @60Hz**. Dùng {@link MovementSystem} làm proxy đại
 * diện (đọc+ghi 2 component mỗi entity, family lookup mỗi tick) — đúng cơ chế lặp/ComponentMapper
 * cốt lõi mà ADR-006 lo ngại (Ashley dùng {@code Bag} + index, không cache-locality lý tưởng theo
 * archetype), KHÔNG cần kéo theo CombatSystem/AiSystem thật (những System đó ở {@code server-world},
 * phụ thuộc NGƯỢC vào {@code engine-ecs} — không thể benchmark từ đây mà không đảo dependency).
 *
 * <p>Chạy: {@code ./gradlew :engine:engine-ecs:jmh} — kết quả JSON ở
 * {@code benchmarks/results/engine-ecs-jmh-result.json} (BENCHMARK.md §7 "Ghi kết quả").
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2)
@org.openjdk.jmh.annotations.Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
public class EcsWorldUpdateBenchmark {

    private static final float FIXED_DELTA_SECONDS = 1f / 60f;

    /**
     * Số entity — ADR-018 60Hz + ECS.md §9 lo ngại Ashley chạm trần ở tải cao. 100 ≈ MVP hiện tại
     * (soak test 2026-07-26 chỉ có ~80 nhân vật); 500/1000/5000 mô phỏng tăng trưởng tương lai để
     * thấy XU HƯỚNG (linear hay tệ hơn) trước khi thật sự chạm ngưỡng ở production.
     */
    @Param({"100", "500", "1000", "5000"})
    public String entityCount;

    private EcsWorld ecsWorld;
    private PositionComponent sampleEntityPosition;

    @Setup(Level.Trial)
    public void setup() {
        ecsWorld = new EcsWorld();
        ecsWorld.addSystem(new MovementSystem());

        int count = Integer.parseInt(entityCount);
        for (int i = 0; i < count; i++) {
            PositionComponent position = new PositionComponent();
            position.x = i;
            position.y = -i;
            VelocityComponent velocity = new VelocityComponent();
            velocity.x = 1f;
            velocity.y = -1f;
            ecsWorld.createEntity().add(position).add(velocity);
            if (i == 0) {
                sampleEntityPosition = position;
            }
        }
    }

    /**
     * {@code return} thay vì {@code void} — chống dead code elimination (BENCHMARK.md §3 "Luôn
     * return hoặc Blackhole.consume()"). Đọc {@code sampleEntityPosition.x} là O(1), không cộng
     * thêm chi phí O(n) làm nhiễu số đo {@code update()} thật.
     */
    @Benchmark
    public float updateOnce() {
        ecsWorld.update(FIXED_DELTA_SECONDS);
        return sampleEntityPosition.x;
    }
}
