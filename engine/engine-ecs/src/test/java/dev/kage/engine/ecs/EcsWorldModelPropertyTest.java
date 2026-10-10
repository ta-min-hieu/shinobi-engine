package dev.kage.engine.ecs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Bộ kiểm THUỘC TÍNH dựa trên mô hình cho {@link EcsWorld} (2026-10-10): chuỗi thao tác ngẫu nhiên
 * có seed chạy song song trên world thật và trên {@link EcsModel}, đối chiếu sau MỖI bước — xem
 * {@link EcsFuzzRun} cho danh sách thao tác và ngữ nghĩa lặp được khẳng định.
 *
 * <p>Bất biến kiểm sau mỗi bước:
 *
 * <ul>
 *   <li>{@code entityCount()} = số entity sống của mô hình (không entity mồ côi, không gỡ thừa).
 *   <li>Mỗi entity sống: {@code ComponentMapper.has/get} và {@code KEntity.has} khớp mô hình theo
 *       từng loại; {@code get} trả ĐÚNG đối tượng đã gắn; component không bao giờ thuộc entity khác.
 *   <li>Handle đã chết không thấy component nào và không ghi được gì — kể cả sau khi entity gốc của
 *       nó được pool cấp lại cho entity mới.
 *   <li>Entity mới sinh ra không mang component nào; id tăng chặt, không tái dùng.
 *   <li>Mỗi System, mỗi tick: không bỏ sót, không lặp trùng, không xử lý entity đã chết, kể cả khi
 *       cấu trúc bị sửa giữa lúc lặp; System chạy đúng thứ tự đăng ký.
 *   <li>Một System ném lỗi giữa lượt không để world kẹt: các bước sau vẫn khớp mô hình.
 * </ul>
 *
 * <p>Ngân sách mặc định vừa cổng {@code check}. Bản dài: {@code -Dkage.ecsFuzz.seeds=3000
 * -Dkage.ecsFuzz.steps=6000 -Djunit.jupiter.execution.timeout.mode=disabled}. Đỏ thì thông báo in
 * seed + vết thao tác; chạy lại đúng seed đó bằng {@code -Dkage.ecsFuzz.seed=<seed>
 * -Dkage.ecsFuzz.seeds=1}.
 */
class EcsWorldModelPropertyTest {

    private static final long BASE_SEED = Long.getLong("kage.ecsFuzz.seed", 0xEC5L);
    private static final int SEEDS = Integer.getInteger("kage.ecsFuzz.seeds", 200);
    private static final int STEPS = Integer.getInteger("kage.ecsFuzz.steps", 2000);

    /** Pool entity của Ashley {@code PooledEngine} giữ tối đa 100; mảng Family khởi đầu 16 chỗ. */
    private static final int ASHLEY_ENTITY_POOL_MAX = 100;

    @Test
    @Timeout(120)
    void shouldMatchNaiveModelAfterEveryStep_underRandomisedStructuralChanges() {
        int maxLive = 0;
        int systemFailures = 0;
        Map<EcsFuzzRun.Op, Integer> totals = new EnumMap<>(EcsFuzzRun.Op.class);
        for (int i = 0; i < SEEDS; i++) {
            EcsFuzzRun run = new EcsFuzzRun(BASE_SEED + i);
            List<String> violations = run.run(STEPS);
            assertThat(violations).as(run::failureReport).isEmpty();
            maxLive = Math.max(maxLive, run.maxLive());
            systemFailures += run.systemFailures();
            for (EcsFuzzRun.Op op : EcsFuzzRun.Op.values()) {
                totals.merge(op, run.count(op), Integer::sum);
            }
        }
        System.out.println("[ecsFuzz] seeds=" + SEEDS + " steps=" + STEPS + " maxLive=" + maxLive + " systemFailures="
                + systemFailures + " ops=" + totals);
        if (SEEDS >= 10 && STEPS >= 1000) {
            assertThat(maxLive)
                    .as("phải vượt dung lượng pool entity của Ashley để chạm cả hai nhánh cấp phát")
                    .isGreaterThan(ASHLEY_ENTITY_POOL_MAX);
            assertThat(systemFailures).as("phải có System ném lỗi giữa lượt").isPositive();
            assertThat(totals.values()).as("mọi loại thao tác đều phải xảy ra").doesNotContain(0);
        }
    }

    /** Cùng seed ⇒ cùng thứ tự System xử lý từng entity: thứ tự lặp không phụ thuộc băm identity. */
    @Test
    @Timeout(60)
    void shouldReplayIdentically_whenTheSameSeedRunsTwice() {
        for (int i = 0; i < 5; i++) {
            long seed = BASE_SEED + i;
            EcsFuzzRun first = new EcsFuzzRun(seed);
            EcsFuzzRun second = new EcsFuzzRun(seed);

            assertThat(first.run(STEPS)).as(first::failureReport).isEmpty();
            assertThat(second.run(STEPS)).as(second::failureReport).isEmpty();

            assertThat(second.digest()).as("dấu vân tay, seed " + seed).isEqualTo(first.digest());
        }
    }
}
