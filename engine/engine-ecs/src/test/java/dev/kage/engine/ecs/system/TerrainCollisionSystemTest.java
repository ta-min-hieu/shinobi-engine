package dev.kage.engine.ecs.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;

class TerrainCollisionSystemTest {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY = ComponentMapper.getFor(VelocityComponent.class);

    private static KEntity movingEntity(EcsWorld world, float startX, float startY, float velocityX) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        entity.add(new VelocityComponent());
        POSITION.get(entity).x = startX;
        POSITION.get(entity).y = startY;
        VELOCITY.get(entity).x = velocityX;
        return entity;
    }

    @Test
    void shouldNotMoveEntity_whenObstacleListIsEmpty() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        world.addSystem(new TerrainCollisionSystem());
        KEntity entity = movingEntity(world, 0f, 0f, 10f);

        world.update(1f);

        // Mặc định RỖNG — hành vi MovementSystem giữ nguyên, không bị TerrainCollisionSystem chặn.
        assertThat(POSITION.get(entity).x).isEqualTo(10f);
    }

    @Test
    void shouldRevertToPreviousTickPosition_whenMovementWouldEnterObstacle() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        TerrainCollisionSystem terrain = new TerrainCollisionSystem();
        terrain.setObstacles(List.of(new Aabb(5f, -1f, 15f, 1f)));
        world.addSystem(terrain);
        KEntity entity = movingEntity(world, 0f, 0f, 10f);

        world.update(1f);

        // Không có va chạm: MovementSystem sẽ đưa x lên 10 (nằm trong obstacle [5,15]) — bị chặn,
        // lùi về đúng vị trí tick trước (0,0), không phải kẹt nửa đường.
        assertThat(POSITION.get(entity).x).isEqualTo(0f);
        assertThat(POSITION.get(entity).y).isEqualTo(0f);
    }

    /**
     * README dòng 72(c): entity nằm SÂU trong vật chặn (sâu hơn một bước đi) được bước ra dần về phía mép
     * gần nhất — trước đây nó kẹt vĩnh viễn vì mọi bước có điểm cuối còn nằm trong đều bị lùi lại.
     */
    @Test
    void shouldLetAnEntityDeepInsideStepOutTowardsTheNearestEdge() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        TerrainCollisionSystem terrain = new TerrainCollisionSystem();
        terrain.setObstacles(List.of(new Aabb(0f, -10f, 10f, 10f)));
        world.addSystem(terrain);
        KEntity entity = movingEntity(world, 7f, 0f, 1f); // mép phải gần nhất (cách 3)

        for (int tick = 0; tick < 4; tick++) {
            world.update(1f);
        }

        assertThat(POSITION.get(entity).x).isEqualTo(11f);
    }

    /** Ngược lại: đi SÂU thêm vào trong vẫn bị chặn, không trượt sâu hơn được. */
    @Test
    void shouldStillBlockAnEntityInsideFromGoingDeeper() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        TerrainCollisionSystem terrain = new TerrainCollisionSystem();
        terrain.setObstacles(List.of(new Aabb(0f, -10f, 10f, 10f)));
        world.addSystem(terrain);
        KEntity entity = movingEntity(world, 7f, 0f, -1f); // đi về phía tâm

        world.update(1f);

        assertThat(POSITION.get(entity).x).isEqualTo(7f);
    }

    @Test
    void shouldAllowMovement_whenDestinationOutsideAnyObstacle() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        TerrainCollisionSystem terrain = new TerrainCollisionSystem();
        terrain.setObstacles(List.of(new Aabb(100f, 100f, 110f, 110f)));
        world.addSystem(terrain);
        KEntity entity = movingEntity(world, 0f, 0f, 10f);

        world.update(1f);

        assertThat(POSITION.get(entity).x).isEqualTo(10f);
    }

    /**
     * Xuyên tường: một tick dời entity QUA HẲN một vật chặn mỏng hơn quãng đi được trong tick đó,
     * nên cả điểm đầu lẫn điểm cuối đều nằm ngoài. Chỉ kiểm điểm cuối thì không bao giờ thấy tường.
     * Ở 60Hz và 5 m/s mỗi tick đi 8,3cm — tường mỏng hơn thế (hoặc một cú lướt nhanh hơn) là xuyên.
     */
    @Test
    void shouldRevert_whenMovementSweepsThroughThinObstacleInOneTick() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        TerrainCollisionSystem terrain = new TerrainCollisionSystem();
        terrain.setObstacles(List.of(new Aabb(5f, -1f, 5.5f, 1f)));
        world.addSystem(terrain);
        KEntity entity = movingEntity(world, 0f, 0f, 10f);

        world.update(1f);

        assertThat(POSITION.get(entity).x).isEqualTo(0f);
    }

    @Test
    void shouldRevert_whenDiagonalMovementSweepsThroughObstacleCorner() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        TerrainCollisionSystem terrain = new TerrainCollisionSystem();
        terrain.setObstacles(List.of(new Aabb(4f, 4f, 6f, 6f)));
        world.addSystem(terrain);
        KEntity entity = movingEntity(world, 0f, 0f, 10f);
        VELOCITY.get(entity).y = 10f;

        world.update(1f);

        assertThat(POSITION.get(entity).x).isEqualTo(0f);
        assertThat(POSITION.get(entity).y).isEqualTo(0f);
    }

    @Test
    void shouldAllowDiagonalMovement_whenSegmentPassesBesideObstacle() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        TerrainCollisionSystem terrain = new TerrainCollisionSystem();
        // Đường chéo (0,0)→(10,10) đi qua (6,6)... khối này nằm hẳn dưới đường chéo, không chạm.
        terrain.setObstacles(List.of(new Aabb(6f, 0f, 10f, 3f)));
        world.addSystem(terrain);
        KEntity entity = movingEntity(world, 0f, 0f, 10f);
        VELOCITY.get(entity).y = 10f;

        world.update(1f);

        assertThat(POSITION.get(entity).x).isEqualTo(10f);
        assertThat(POSITION.get(entity).y).isEqualTo(10f);
    }

    /** Entity lỡ nằm trong vật chặn (dựng sai, dữ liệu map mới) phải đi RA được — không kẹt vĩnh viễn. */
    @Test
    void shouldAllowLeavingObstacle_whenEntityStartsInside() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        TerrainCollisionSystem terrain = new TerrainCollisionSystem();
        terrain.setObstacles(List.of(new Aabb(-1f, -1f, 1f, 1f)));
        world.addSystem(terrain);
        KEntity entity = movingEntity(world, 0f, 0f, 5f);

        world.update(1f);

        assertThat(POSITION.get(entity).x).isEqualTo(5f);
    }

    @Test
    void containsShouldIncludeBoundary() {
        Aabb box = new Aabb(0f, 0f, 10f, 10f);

        assertThat(box.contains(0f, 0f)).isTrue();
        assertThat(box.contains(10f, 10f)).isTrue();
        assertThat(box.contains(5f, 5f)).isTrue();
        assertThat(box.contains(10.1f, 5f)).isFalse();
        assertThat(box.contains(-0.1f, 5f)).isFalse();
    }
}
