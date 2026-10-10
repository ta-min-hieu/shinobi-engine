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
import dev.kage.engine.ecs.system.SegmentOracle.Segment;
import dev.kage.engine.ecs.system.SegmentOracle.Verdict;

/**
 * Bộ kiểm THUỘC TÍNH cho va chạm địa hình (2026-10-10): {@link Aabb#intersectsSegment} đối chiếu với
 * {@link SegmentOracle}, rồi {@link MovementSystem} + {@link TerrainCollisionSystem} chạy thật trên
 * {@link EcsWorld} với tường ngẫu nhiên và vận tốc ngẫu nhiên (kể cả cú lướt hàng trăm m/s).
 *
 * <p>Toạ độ rút từ lưới 0,25m trộn với số thực bất kỳ: lưới cho ra đúng các ca biên (đoạn thẳng đứng
 * hoặc nằm ngang, đầu mút nằm đúng trên cạnh, sượt đúng góc), số thực cho ra ca tổng quát.
 *
 * <p>Chạy lại một seed đỏ: {@code -Dkage.ecsFuzz.seed=<seed> -Dkage.ecsFuzz.seeds=1}.
 */
class TerrainCollisionPropertyTest {

    private static final long BASE_SEED = Long.getLong("kage.ecsFuzz.seed", 0x7E44A1L);
    private static final int SEEDS = Integer.getInteger("kage.ecsFuzz.seeds", 200);
    private static final int CASES_PER_SEED = 2_000;
    private static final int TICKS_PER_SEED = 2_000;
    private static final float FIXED_DT = 1f / 60f;
    private static final float WORLD_HALF_SIZE = 40f;
    private static final float GRID = 0.25f;

    private final List<Aabb> obstacles = new ArrayList<>();
    private final PositionComponent position = new PositionComponent();
    private final VelocityComponent velocity = new VelocityComponent();
    private int crossings;
    private int misses;
    private int blockedMoves;
    private int freeMoves;

    @Test
    @Timeout(60)
    void segmentTest_shouldAgreeWithOracle_whereverTheOracleIsDecisive() {
        for (int i = 0; i < SEEDS; i++) {
            long seed = BASE_SEED + i;
            SplittableRandom random = new SplittableRandom(seed);
            for (int n = 0; n < CASES_PER_SEED; n++) {
                Aabb box = randomBox(random);
                float x0 = coordinate(random);
                float y0 = coordinate(random);
                float x1 = random.nextInt(8) == 0 ? x0 : coordinate(random);
                float y1 = random.nextInt(8) == 0 ? y0 : coordinate(random);
                checkSegment(box, new float[] {x0, y0, x1, y1}, "seed=" + seed + " case=" + n);
            }
        }
        System.out.println("[terrainFuzz] đoạn xuyên chắc=" + crossings + " trượt chắc=" + misses);
        assertThat(crossings).as("phải có ca đoạn xuyên hộp").isPositive();
        assertThat(misses).as("phải có ca đoạn trượt hộp").isPositive();
    }

    private void checkSegment(Aabb box, float[] ends, String where) {
        String description =
                where + " " + box + " đoạn (" + ends[0] + "," + ends[1] + ")→(" + ends[2] + "," + ends[3] + ")";

        boolean actual = box.intersectsSegment(ends[0], ends[1], ends[2], ends[3]);

        Verdict verdict = SegmentOracle.judge(box, new Segment(ends[0], ends[1], ends[2], ends[3]));
        if (verdict == Verdict.CLEARLY_CROSSES) {
            crossings++;
            assertThat(actual).as("phải báo chạm: " + description).isTrue();
        } else if (verdict == Verdict.CLEARLY_MISSES) {
            misses++;
            assertThat(actual).as("phải báo không chạm: " + description).isFalse();
        }
        if (box.contains(ends[0], ends[1]) || box.contains(ends[2], ends[3])) {
            assertThat(actual).as("đầu mút nằm trong hộp: " + description).isTrue();
        }
        assertThat(box.intersectsSegment(ends[2], ends[3], ends[0], ends[1]))
                .as("đổi chiều đoạn không được đổi kết quả: " + description)
                .isEqualTo(actual);
    }

    /**
     * Mỗi tick, với entity bắt đầu tick ở NGOÀI mọi vật chặn: không kết thúc tick trong vật chặn nào,
     * không đi xuyên vật chặn nào; và nếu đường đi cách xa mọi vật chặn thì bước đi phải được giữ
     * nguyên (tường không được chặn người không chạm vào nó).
     */
    @Test
    @Timeout(60)
    void movingEntity_shouldNeverEnterOrCrossAnObstacle_whenItStartsTheTickOutside() {
        for (int i = 0; i < SEEDS; i++) {
            long seed = BASE_SEED + i;
            SplittableRandom random = new SplittableRandom(seed);
            EcsWorld world = randomWorld(random);
            for (int tick = 0; tick < TICKS_PER_SEED; tick++) {
                steer(random);
                float startX = position.x;
                float startY = position.y;
                Segment wanted =
                        new Segment(startX, startY, startX + velocity.x * FIXED_DT, startY + velocity.y * FIXED_DT);

                world.update(FIXED_DT);

                checkStep(wanted, "seed=" + seed + " tick=" + tick);
            }
        }
        System.out.println("[terrainFuzz] bước bị chặn=" + blockedMoves + " bước đi được=" + freeMoves);
        assertThat(blockedMoves).as("phải có bước bị tường chặn").isPositive();
        assertThat(freeMoves).as("phải có bước đi được").isPositive();
    }

    private EcsWorld randomWorld(SplittableRandom random) {
        obstacles.clear();
        int obstacleCount = 1 + random.nextInt(6);
        for (int n = 0; n < obstacleCount; n++) {
            obstacles.add(randomBox(random));
        }
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        TerrainCollisionSystem terrain = new TerrainCollisionSystem();
        terrain.setObstacles(obstacles);
        world.addSystem(terrain);
        position.x = coordinate(random);
        position.y = coordinate(random);
        velocity.x = 0f;
        velocity.y = 0f;
        world.createEntity().add(position).add(velocity);
        return world;
    }

    private void checkStep(Segment wanted, String where) {
        boolean moved = differ(position.x, wanted.x0()) || differ(position.y, wanted.y0());
        boolean wantedToMove = differ(wanted.x0(), wanted.x1()) || differ(wanted.y0(), wanted.y1());
        blockedMoves += (wantedToMove && !moved) ? 1 : 0;
        freeMoves += moved ? 1 : 0;
        if (insideAny(wanted.x0(), wanted.y0())) {
            return; // bắt đầu tick trong vật chặn — ngoài phạm vi bất biến này
        }
        String description = where + " " + wanted + " vật chặn " + obstacles;
        assertThat(insideAny(position.x, position.y))
                .as("kết thúc tick trong vật chặn: " + description)
                .isFalse();
        Segment travelled = new Segment(wanted.x0(), wanted.y0(), position.x, position.y);
        assertThat(worstVerdict(travelled))
                .as("đi xuyên vật chặn: " + description)
                .isNotEqualTo(Verdict.CLEARLY_CROSSES);
        if (worstVerdict(wanted) == Verdict.CLEARLY_MISSES) {
            assertThat(moved || !wantedToMove).as("bị chặn oan: " + description).isTrue();
            assertThat(differ(position.x, wanted.x1()) || differ(position.y, wanted.y1()))
                    .as("bước đi bị đổi: " + description)
                    .isFalse();
        }
    }

    /** So sánh ĐÚNG từng bit — hai vế cùng sinh từ một phép tính float, không phải hai số trôi dạt. */
    private static boolean differ(double left, double right) {
        return Double.compare(left, right) != 0;
    }

    /** Đổi hướng thỉnh thoảng; đa số đi bộ, đôi khi lướt nhanh hơn bề dày của mọi bức tường. */
    private void steer(SplittableRandom random) {
        if (Math.abs(position.x) > WORLD_HALF_SIZE || Math.abs(position.y) > WORLD_HALF_SIZE) {
            velocity.x = -Math.signum(position.x) * 5f;
            velocity.y = -Math.signum(position.y) * 5f;
            return;
        }
        if (random.nextInt(6) != 0) {
            return;
        }
        float speed =
                switch (random.nextInt(4)) {
                    case 0 -> 0f;
                    case 1 -> 5f;
                    case 2 -> 60f;
                    default -> 900f;
                };
        double angle = random.nextInt(4) == 0 ? (Math.PI / 2) * random.nextInt(4) : random.nextDouble() * 2 * Math.PI;
        velocity.x = (float) (Math.cos(angle) * speed);
        velocity.y = (float) (Math.sin(angle) * speed);
    }

    private boolean insideAny(double x, double y) {
        for (Aabb obstacle : obstacles) {
            if (obstacle.contains((float) x, (float) y)) {
                return true;
            }
        }
        return false;
    }

    /** CLEARLY_CROSSES nếu xuyên chắc một vật chặn; CLEARLY_MISSES chỉ khi cách xa TẤT CẢ. */
    private Verdict worstVerdict(Segment segment) {
        Verdict worst = Verdict.CLEARLY_MISSES;
        for (Aabb obstacle : obstacles) {
            Verdict verdict = SegmentOracle.judge(obstacle, segment);
            if (verdict == Verdict.CLEARLY_CROSSES) {
                return verdict;
            }
            if (verdict == Verdict.TOO_CLOSE_TO_CALL) {
                worst = verdict;
            }
        }
        return worst;
    }

    private static Aabb randomBox(SplittableRandom random) {
        float minX = coordinate(random);
        float minY = coordinate(random);
        float width = random.nextInt(5) == 0 ? 0.05f : random.nextFloat() * 12f;
        float height = random.nextInt(5) == 0 ? 0.05f : random.nextFloat() * 12f;
        return new Aabb(minX, minY, minX + width, minY + height);
    }

    private static float coordinate(SplittableRandom random) {
        float value = (random.nextFloat() * 2f - 1f) * WORLD_HALF_SIZE;
        return random.nextBoolean() ? Math.round(value / GRID) * GRID : value;
    }
}
