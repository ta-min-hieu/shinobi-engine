package dev.kage.engine.ecs;

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

import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.system.VerticalMovementSystem;

/**
 * Micro-benchmark {@code VerticalMovementSystem} (ADR-033 "Việc cần làm tiếp theo" #6 — Điều 9,
 * benchmark trước khi kết luận). Đo chi phí CPU CẬN BIÊN mà hệ thống trọng lực/nhảy thêm vào tick
 * — proxy cho câu hỏi "vật lý nhảy có đẩy {@code kage.tick.duration} vượt ngân sách không", cùng
 * phương pháp {@link EcsWorldUpdateBenchmark} đã dùng để đo Revisit Trigger ADR-006 (BENCHMARK.md
 * §4). KHÔNG thay thế đo {@code kage.tick.duration} thật ở N tham chiếu Phase 9 qua
 * {@code WorldTickScheduler} production (heavier, cần server thật chạy — vẫn là việc riêng, xem
 * ADR-033 mục #6) — đây chỉ cô lập đúng 1 System mới thêm vào, không đại diện toàn bộ tick.
 *
 * <p>Mọi entity {@code airborne=true} — kịch bản XẤU NHẤT (grounded+vz=0 đi qua fast-path no-op
 * của {@code VerticalMovementSystem.processEntity}, không tốn gì đáng kể). Đo worst-case: toàn bộ
 * N người chơi đang nhảy CÙNG lúc, dù thực tế không bao giờ xảy ra (nhảy chỉ ~0.7s/lần).
 *
 * <p>Chạy: {@code ./gradlew :engine:engine-ecs:jmh}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2)
@org.openjdk.jmh.annotations.Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
public class VerticalMovementBenchmark {

    private static final float FIXED_DELTA_SECONDS = 1f / 60f;
    private static final float GRAVITY_MPS2 = 20f; // khớp server-world.WorldConstants.GRAVITY_MPS2

    /** Cùng dải N với {@link EcsWorldUpdateBenchmark} — so sánh trực tiếp được. */
    @Param({"100", "500", "1000", "5000"})
    public String entityCount;

    private EcsWorld ecsWorld;
    private PositionComponent sampleEntityPosition;

    @Setup(Level.Trial)
    public void setup() {
        ecsWorld = new EcsWorld();
        ecsWorld.addSystem(new VerticalMovementSystem(GRAVITY_MPS2));

        int count = Integer.parseInt(entityCount);
        for (int i = 0; i < count; i++) {
            PositionComponent position = new PositionComponent();
            position.x = i;
            position.y = -i;
            position.z = 1f; // lơ lửng — buộc đi qua nhánh tính toán, không fast-path
            position.grounded = false;
            position.vz = 1f; // đang lên, đủ để không lập tức đáp đất ở tick đầu
            ecsWorld.createEntity().add(position);
            if (i == 0) {
                sampleEntityPosition = position;
            }
        }
    }

    /**
     * {@code return} thay vì {@code void} — chống dead code elimination (BENCHMARK.md §3). Đọc
     * {@code sampleEntityPosition.z} là O(1), không cộng thêm chi phí O(n) làm nhiễu số đo.
     */
    @Benchmark
    public float updateOnce() {
        ecsWorld.update(FIXED_DELTA_SECONDS);
        return sampleEntityPosition.z;
    }
}
