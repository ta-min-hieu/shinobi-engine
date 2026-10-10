package dev.kage.engine.ecs;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.TriggerZoneMembershipComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.Aabb;
import dev.kage.engine.ecs.system.MovementSystem;
import dev.kage.engine.ecs.system.NamedZone;
import dev.kage.engine.ecs.system.Platform;
import dev.kage.engine.ecs.system.TerrainCollisionSystem;
import dev.kage.engine.ecs.system.TriggerZoneSystem;
import dev.kage.engine.ecs.system.VerticalMovementSystem;

/**
 * Đo số byte cấp phát MỖI TICK của đường nóng engine-ecs — luật "không {@code new} trong game loop"
 * (/CLAUDE.md §6) phải được ĐO chứ không suy ra từ đọc code (§9). Cùng cách đo với
 * {@code FrameAllocationTest} của client-core: bộ đếm byte cấp phát của chính luồng test
 * ({@link com.sun.management.ThreadMXBean#getCurrentThreadAllocatedBytes()}), sau warm-up đủ dài để
 * C2 biên dịch các hàm nóng.
 *
 * <p>Cảnh: {@value #ENTITIES} entity đang đi và đang nhảy, bốn System có sẵn của engine (di chuyển,
 * trọng lực + bệ, va chạm địa hình có vật chặn, vùng kích hoạt), đúng thứ tự server đăng ký.
 */
class EcsTickAllocationTest {

    private static final int ENTITIES = 200;
    private static final int WARMUP_TICKS = 30_000;
    private static final int MEASURED_TICKS = 5_000;
    private static final int ROUNDS = 5;
    private static final float FIXED_DT = 1f / 60f;
    private static final float GRAVITY = 30f;
    private static final float JUMP_SPEED = 9f;
    private static final float ARENA_HALF_WIDTH = 40f;

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY = ComponentMapper.getFor(VelocityComponent.class);

    /** Một việc chạy mỗi tick cần đo. */
    @FunctionalInterface
    private interface TickWork {
        void run();
    }

    /** Sự kiện thử — dựng sẵn một lần, publish lại mỗi tick. */
    private record ProbeEvent(long entityId) implements Event {}

    /** Giữ cảnh sống mãi: đổi chiều ở mép sân, bật nhảy lại mỗi lần chạm đất. Không cấp phát. */
    private static final class KeepMovingSystem extends IteratingKSystem {
        KeepMovingSystem() {
            super(Family.all(PositionComponent.class, VelocityComponent.class).get());
        }

        @Override
        protected void processEntity(KEntity entity, float deltaTime) {
            PositionComponent position = POSITION.get(entity);
            VelocityComponent velocity = VELOCITY.get(entity);
            if (position.x > ARENA_HALF_WIDTH || position.x < -ARENA_HALF_WIDTH) {
                velocity.x = -velocity.x;
            }
            if (position.grounded && position.vz == 0f) {
                position.vz = JUMP_SPEED;
                position.grounded = false;
            }
        }
    }

    private int sink;

    /** Dựng MỘT lần: lambda bắt {@code this} mà viết ngay tại chỗ gọi thì chính nó cấp phát mỗi tick. */
    private final EcsWorld.SystemTimingListener timingListener = (systemName, nanos) -> sink += systemName.length();

    @Test
    void worldUpdate_isAllocationFree_whenNoEntityCrossesAZoneBoundary() {
        EcsWorld world = sceneWithoutZones();

        assertThat(measure("baseline (empty)", () -> {})).isZero();
        assertThat(measure("EcsWorld.update, 5 System x " + ENTITIES + " entity", () -> world.update(FIXED_DT)))
                .isZero();
        assertThat(measure("EcsWorld.update + SystemTimingListener", () -> world.update(FIXED_DT, timingListener)))
                .isZero();
    }

    /**
     * Vào/ra vùng là SỰ KIỆN chứ không phải việc mỗi tick, nhưng mỗi lần vào thì {@code HashSet} của
     * {@link TriggerZoneMembershipComponent} cấp một nút mới. Ca này ghi lại con số đó để ai đổi cấu
     * trúc dữ liệu có mốc so sánh — trần rộng, chỉ chặn một lần cấp phát cho MỖI entity MỖI tick.
     */
    @Test
    void worldUpdate_allocatesOnlyOnZoneEntry_whenEntitiesKeepCrossingZones() {
        EcsWorld world = sceneWithoutZones();
        TriggerZoneSystem zones = new TriggerZoneSystem(
                (entity, zoneId) -> sink += zoneId.length(), (entity, zoneId) -> sink -= zoneId.length());
        zones.setZones(List.of(
                new NamedZone("west", new Aabb(-30f, -5f, -10f, 5f)),
                new NamedZone("east", new Aabb(10f, -5f, 30f, 5f))));
        world.addSystem(zones);

        long bytesPerTick =
                measure("EcsWorld.update + vùng kích hoạt đang có người ra vào", () -> world.update(FIXED_DT));

        assertThat(bytesPerTick).isLessThan(ENTITIES * 16L);
    }

    @Test
    void eventBusPublish_isAllocationFree() {
        EventBus bus = new EventBus();
        for (int i = 0; i < 3; i++) {
            bus.subscribe(ProbeEvent.class, event -> sink += (int) event.entityId());
        }
        ProbeEvent event = new ProbeEvent(7L);

        assertThat(measure("EventBus.publish, 3 listener", () -> bus.publish(event)))
                .isZero();
    }

    private static EcsWorld sceneWithoutZones() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new KeepMovingSystem());
        world.addSystem(new MovementSystem());
        VerticalMovementSystem vertical = new VerticalMovementSystem(GRAVITY);
        vertical.setPlatforms(List.of(
                new Platform(new Aabb(-20f, -1f, -12f, 1f), 1.5f), new Platform(new Aabb(12f, -1f, 20f, 1f), 3f)));
        world.addSystem(vertical);
        TerrainCollisionSystem terrain = new TerrainCollisionSystem();
        terrain.setObstacles(List.of(new Aabb(-60f, -1f, -50f, 1f), new Aabb(50f, -1f, 60f, 1f)));
        world.addSystem(terrain);
        for (int i = 0; i < ENTITIES; i++) {
            PositionComponent position = new PositionComponent();
            position.x = -ARENA_HALF_WIDTH + (2f * ARENA_HALF_WIDTH * i) / ENTITIES;
            VelocityComponent velocity = new VelocityComponent();
            velocity.x = (i % 2 == 0) ? 5f : -5f;
            world.createEntity().add(position).add(velocity).add(new TriggerZoneMembershipComponent());
        }
        return world;
    }

    private long measure(String name, TickWork work) {
        for (int i = 0; i < WARMUP_TICKS; i++) {
            work.run();
        }
        long best = Long.MAX_VALUE;
        for (int round = 0; round < ROUNDS; round++) {
            long total = 0L;
            for (int i = 0; i < MEASURED_TICKS; i++) {
                long before = THREADS.getCurrentThreadAllocatedBytes();
                work.run();
                total += THREADS.getCurrentThreadAllocatedBytes() - before;
            }
            best = Math.min(best, total);
        }
        long bytesPerTick = best / MEASURED_TICKS;
        System.out.println("[ecsAlloc] " + name + " = " + bytesPerTick + " byte/tick (min của " + ROUNDS + " vòng x "
                + MEASURED_TICKS + " tick, tổng vòng tốt nhất " + best + " byte, sink " + sink + ")");
        return bytesPerTick;
    }
}
