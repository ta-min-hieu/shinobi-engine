package dev.kage.engine.ecs.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;

class VerticalMovementSystemTest {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final float GRAVITY_MPS2 = 10f;

    /** Entity ở gốc toạ độ (0,0) — đủ cho mọi test trừ {@link #shouldFallPastPlatform_whenOutsideItsFootprint}. */
    private static KEntity entityAt(EcsWorld world, float z, boolean grounded, float vz) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        PositionComponent pos = POSITION.get(entity);
        pos.z = z;
        pos.grounded = grounded;
        pos.vz = vz;
        return entity;
    }

    @Test
    void shouldStayGrounded_whenNoVerticalVelocity() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new VerticalMovementSystem(GRAVITY_MPS2));
        KEntity entity = entityAt(world, 0f, true, 0f);

        world.update(1f);

        assertThat(POSITION.get(entity).z).isZero();
        assertThat(POSITION.get(entity).grounded).isTrue();
    }

    @Test
    void shouldFallBackToFlatGround_whenAirborneWithNoPlatforms() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new VerticalMovementSystem(GRAVITY_MPS2));
        KEntity entity = entityAt(world, 5f, false, 0f);

        // dt lớn (1s) đủ để rơi hết 5m dù trần chậm — chỉ cần đáp đúng z=0, không tunnel qua mặt đất.
        world.update(1f);

        assertThat(POSITION.get(entity).z).isZero();
        assertThat(POSITION.get(entity).grounded).isTrue();
        assertThat(POSITION.get(entity).vz).isZero();
    }

    @Test
    void shouldRiseThenLoseHeightToGravity_whenJumping() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new VerticalMovementSystem(GRAVITY_MPS2));
        KEntity entity = entityAt(world, 0f, false, 8f); // vz ban đầu = lực nhảy giả lập

        world.update(0.1f); // tick nhỏ — vz sau tick: 8 - 10*0.1 = 7; z: 0 + 7*0.1 = 0.7

        assertThat(POSITION.get(entity).grounded).isFalse();
        assertThat(POSITION.get(entity).z).isCloseTo(0.7f, org.assertj.core.data.Offset.offset(1e-4f));
    }

    @Test
    void shouldLandOnPlatform_whenFallingOntoItsFootprint() {
        EcsWorld world = new EcsWorld();
        VerticalMovementSystem vertical = new VerticalMovementSystem(GRAVITY_MPS2);
        vertical.setPlatforms(List.of(new Platform(new Aabb(-2f, -2f, 2f, 2f), 3f)));
        world.addSystem(vertical);
        KEntity entity = entityAt(world, 10f, false, 0f);

        // dt lớn (1s) đủ để "xuyên" cả mặt bệ lẫn mặt đất trong 1 tick nếu tính thô — hệ thống phải
        // bắt đúng bệ CAO NHẤT nằm dưới độ cao đầu tick, không rơi thẳng xuống z=0.
        world.update(1f);

        assertThat(POSITION.get(entity).z).isEqualTo(3f);
        assertThat(POSITION.get(entity).grounded).isTrue();
    }

    /**
     * Bug THẬT chủ dự án phát hiện khi test tay (2026-08-09): đứng trên bệ rồi đi ngang ra khỏi mép
     * thì lơ lửng giữa không trung mãi mãi, vì {@code processEntity} thoát sớm vô điều kiện khi
     * {@code grounded && vz == 0} nên trọng lực không bao giờ bật lại.
     */
    @Test
    void shouldStartFalling_whenWalkingOffPlatformEdge() {
        EcsWorld world = new EcsWorld();
        VerticalMovementSystem vertical = new VerticalMovementSystem(GRAVITY_MPS2);
        vertical.setPlatforms(List.of(new Platform(new Aabb(-2f, -2f, 2f, 2f), 3f)));
        world.addSystem(vertical);
        KEntity entity = entityAt(world, 3f, true, 0f); // đang ĐỨNG YÊN trên mặt bệ (z = topZ)

        world.update(0.1f);
        assertThat(POSITION.get(entity).z).isEqualTo(3f); // trong footprint — vẫn được đỡ
        assertThat(POSITION.get(entity).grounded).isTrue();

        POSITION.get(entity).x = 50f; // MovementSystem đưa ra ngoài mép bệ trong tick kế tiếp
        world.update(1f);

        assertThat(POSITION.get(entity).grounded).isTrue(); // đã rơi xuống và đáp đất
        assertThat(POSITION.get(entity).z).isZero();
    }

    /**
     * Hạn mức nhảy giữa không trung (nhảy đôi, 2026-08-09) tính THEO MỖI LẦN rời đất — hệ này là
     * nơi duy nhất biết khoảnh khắc đáp đất nên cũng là nơi xoá hạn mức. Hỏng chỗ này = người chơi
     * chỉ nhảy đôi được đúng một lần rồi thôi cho tới khi hồi sinh.
     */
    @Test
    void shouldResetAirJumpBudget_whenLanding() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new VerticalMovementSystem(GRAVITY_MPS2));
        KEntity entity = entityAt(world, 2f, false, 0f);
        POSITION.get(entity).airJumpsUsed = 1; // đã tiêu hạn mức trong cú nhảy vừa rồi

        world.update(1f); // đủ để rơi 2m và đáp đất

        assertThat(POSITION.get(entity).grounded).isTrue();
        assertThat(POSITION.get(entity).airJumpsUsed).isZero();
    }

    /** Còn đang lơ lửng thì hạn mức KHÔNG được tự đầy lại — nếu không, giữ nút = bay vô hạn. */
    @Test
    void shouldKeepAirJumpBudgetSpent_whileStillAirborne() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new VerticalMovementSystem(GRAVITY_MPS2));
        KEntity entity = entityAt(world, 10f, false, 0f);
        POSITION.get(entity).airJumpsUsed = 1;

        world.update(0.1f); // vẫn còn cách mặt đất rất xa

        assertThat(POSITION.get(entity).grounded).isFalse();
        assertThat(POSITION.get(entity).airJumpsUsed).isEqualTo(1);
    }

    @Test
    void shouldFallPastPlatform_whenOutsideItsFootprint() {
        EcsWorld world = new EcsWorld();
        VerticalMovementSystem vertical = new VerticalMovementSystem(GRAVITY_MPS2);
        vertical.setPlatforms(List.of(new Platform(new Aabb(-2f, -2f, 2f, 2f), 3f)));
        world.addSystem(vertical);
        KEntity entity = entityAt(world, 10f, false, 0f);
        POSITION.get(entity).x = 50f; // ngoài footprint bệ
        POSITION.get(entity).y = 50f;

        world.update(1f);

        assertThat(POSITION.get(entity).z).isZero();
        assertThat(POSITION.get(entity).grounded).isTrue();
    }
}
