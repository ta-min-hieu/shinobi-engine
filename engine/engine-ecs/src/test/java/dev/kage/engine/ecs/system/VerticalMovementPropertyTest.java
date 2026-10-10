package dev.kage.engine.ecs.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;

/**
 * Bộ kiểm THUỘC TÍNH cho trục đứng (2026-10-10): một entity chạy, nhảy, nhảy đôi ngẫu nhiên trên bệ
 * ngẫu nhiên, {@link MovementSystem} + {@link VerticalMovementSystem} chạy thật trên {@link EcsWorld}.
 * Bất biến kiểm sau MỖI tick:
 *
 * <ul>
 *   <li>{@code z}, {@code vz} hữu hạn; không bao giờ chìm dưới mặt đất {@code z=0}.
 *   <li>Không rơi xuyên bệ: đầu tick ở trên hoặc ngang mặt một bệ đang phủ chỗ đứng thì cuối tick
 *       không ở dưới mặt bệ đó.
 *   <li>Đang đứng ({@code grounded}) thì {@code vz=0}, hạn mức nhảy đôi đã xoá, và dưới chân PHẢI
 *       có mặt đỡ thật (mặt đất hoặc đúng mặt một bệ phủ chỗ đứng) — không lơ lửng sau khi rời mép bệ.
 *   <li>Đang bay thì ở trên mặt đất; không ai bay mãi (mỗi lần rời đất đều có lúc chạm lại).
 *   <li>{@code prevZ} luôn là {@code z} đầu tick (client nội suy theo nó).
 * </ul>
 *
 * <p>Chạy lại một seed đỏ: {@code -Dkage.ecsFuzz.seed=<seed> -Dkage.ecsFuzz.seeds=1}.
 */
class VerticalMovementPropertyTest {

    private static final long BASE_SEED = Long.getLong("kage.ecsFuzz.seed", 0x2A11L);
    private static final int SEEDS = Integer.getInteger("kage.ecsFuzz.seeds", 200);
    private static final int TICKS_PER_SEED = 3_000;
    private static final float FIXED_DT = 1f / 60f;
    private static final float GRAVITY = 30f;
    private static final float JUMP_SPEED = 9f;
    private static final int MAX_AIR_JUMPS = 1;
    private static final float TRACK_HALF_LENGTH = 20f;

    /** Nhảy + nhảy đôi từ bệ cao nhất rơi về đất mất chưa tới 2 giây; 5 giây là kẹt trên không. */
    private static final int MAX_AIRBORNE_TICKS = 300;

    private final List<Platform> platforms = new ArrayList<>();
    private final PositionComponent position = new PositionComponent();
    private final VelocityComponent velocity = new VelocityComponent();
    private int airborneTicks;
    private int landings;
    private int platformLandings;

    @Test
    @Timeout(60)
    void shouldKeepVerticalInvariants_underRandomRunningAndJumping() {
        for (int i = 0; i < SEEDS; i++) {
            runSeed(BASE_SEED + i);
        }
        System.out.println("[verticalFuzz] lần chạm đất=" + landings + " trong đó trên bệ=" + platformLandings);
        assertThat(platformLandings).as("phải có lần đáp lên bệ").isPositive();
        assertThat(landings).as("phải có lần đáp xuống mặt đất").isGreaterThan(platformLandings);
    }

    private void runSeed(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        platforms.clear();
        int count = random.nextInt(6);
        for (int n = 0; n < count; n++) {
            float minX = (random.nextFloat() * 2f - 1f) * TRACK_HALF_LENGTH;
            float topZ = 0.25f * (1 + random.nextInt(24));
            platforms.add(new Platform(new Aabb(minX, -1f, minX + 0.5f + random.nextFloat() * 6f, 1f), topZ));
        }
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        VerticalMovementSystem vertical = new VerticalMovementSystem(GRAVITY);
        vertical.setPlatforms(platforms);
        world.addSystem(vertical);
        reset();
        world.createEntity().add(position).add(velocity);

        for (int tick = 0; tick < TICKS_PER_SEED; tick++) {
            steer(random);
            float startZ = position.z;
            boolean startedGrounded = position.grounded;

            world.update(FIXED_DT);

            String description = "seed=" + seed + " tick=" + tick + " x=" + position.x + " z: " + startZ + "→"
                    + position.z + " vz=" + position.vz + " grounded=" + position.grounded + " bệ=" + platforms;
            assertThat(position.prevZ).as("prevZ: " + description).isEqualTo(startZ);
            checkHeight(startZ, description);
            checkStance(description);
            countLanding(startedGrounded);
        }
    }

    private void reset() {
        position.x = 0f;
        position.y = 0f;
        position.z = 0f;
        position.vz = 0f;
        position.grounded = true;
        position.airJumpsUsed = 0;
        velocity.x = 0f;
        velocity.y = 0f;
        airborneTicks = 0;
    }

    /** Cùng cách lớp gọi thật bật nhảy: đặt {@code vz}, bỏ {@code grounded}; nhảy đôi thì tính hạn mức. */
    private void steer(SplittableRandom random) {
        if (Math.abs(position.x) > TRACK_HALF_LENGTH) {
            velocity.x = -Math.signum(position.x) * 5f;
        } else if (random.nextInt(20) == 0) {
            velocity.x = (random.nextInt(5) - 2) * (random.nextInt(6) == 0 ? 40f : 5f);
        }
        if (random.nextInt(25) != 0) {
            return;
        }
        if (position.grounded) {
            position.vz = JUMP_SPEED;
            position.grounded = false;
        } else if (position.airJumpsUsed < MAX_AIR_JUMPS) {
            position.vz = JUMP_SPEED;
            position.airJumpsUsed++;
        }
    }

    private void checkHeight(float startZ, String description) {
        assertThat(Float.isFinite(position.z) && Float.isFinite(position.vz))
                .as("z/vz hữu hạn: " + description)
                .isTrue();
        assertThat(position.z).as("chìm dưới mặt đất: " + description).isGreaterThanOrEqualTo(0f);
        for (Platform platform : platforms) {
            if (platform.contains(position.x, position.y) && startZ >= platform.topZ()) {
                assertThat(position.z)
                        .as("rơi xuyên bệ " + platform + ": " + description)
                        .isGreaterThanOrEqualTo(platform.topZ());
            }
        }
    }

    private void checkStance(String description) {
        if (!position.grounded) {
            airborneTicks++;
            assertThat(position.z)
                    .as("bay mà không ở trên mặt đất: " + description)
                    .isGreaterThan(0f);
            assertThat(airborneTicks)
                    .as("bay mãi không chạm đất: " + description)
                    .isLessThan(MAX_AIRBORNE_TICKS);
            return;
        }
        airborneTicks = 0;
        assertThat(position.vz).as("đứng mà vz khác 0: " + description).isZero();
        assertThat(position.airJumpsUsed)
                .as("đứng mà còn nợ nhảy đôi: " + description)
                .isZero();
        assertThat(standsOnSomething()).as("đứng lơ lửng: " + description).isTrue();
    }

    private boolean standsOnSomething() {
        if (position.z == 0f) {
            return true;
        }
        for (Platform platform : platforms) {
            if (platform.contains(position.x, position.y) && platform.topZ() == position.z) {
                return true;
            }
        }
        return false;
    }

    private void countLanding(boolean startedGrounded) {
        if (!startedGrounded && position.grounded) {
            landings++;
            platformLandings += position.z > 0f ? 1 : 0;
        }
    }
}
