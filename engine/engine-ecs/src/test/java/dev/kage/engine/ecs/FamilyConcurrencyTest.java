package dev.kage.engine.ecs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Dựng {@link Family} từ NHIỀU luồng cùng lúc — mỗi World của server chạy trên luồng riêng và được
 * dựng trên luồng của lệnh sinh ra nó (trận đấu trường, hầm ngục, khu vực mới của world chính), nên
 * hai World dựng cùng lúc là chuyện thường.
 *
 * <p>Bug thật (2026-10-10, tìm bằng {@code DisconnectAnywhereFuzzDatabaseTest}): {@code Family.all}
 * của Ashley ghi vào MỘT builder tĩnh dùng chung rồi {@code get()} đọc lại nó. Hai luồng đan xen thì
 * luồng này nhận Family của luồng kia: System của một World lọc nhầm tập entity — {@code BossAiSystem}
 * chạy trên entity không có {@code BossAiComponent} và ném {@code NullPointerException} MỌI tick, hoặc
 * tệ hơn là một System lặng lẽ không bao giờ chạy trong World đó.
 *
 * <p>Phép đo: Ashley trả cùng MỘT đối tượng cho cùng một tập component, nên Family dựng giữa lúc
 * chen nhau phải là đúng đối tượng đã dựng lúc còn một luồng.
 */
@Timeout(60)
class FamilyConcurrencyTest {

    private static final int THREADS = 4;
    private static final int ROUNDS_PER_THREAD = 20_000;

    private static final class FirstComponent implements Component {}

    private static final class SecondComponent implements Component {}

    private static final class ThirdComponent implements Component {}

    private static final class FourthComponent implements Component {}

    private static final List<Class<? extends Component>> TYPES =
            List.of(FirstComponent.class, SecondComponent.class, ThirdComponent.class, FourthComponent.class);

    @Test
    void shouldGiveEveryThreadItsOwnFamily_whenFamiliesAreBuiltConcurrently() throws Exception {
        List<Object> expected = new ArrayList<>();
        for (Class<? extends Component> type : TYPES) {
            expected.add(Family.all(type).get().raw());
        }

        CyclicBarrier start = new CyclicBarrier(THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Integer>> wrongFamilies = new ArrayList<>();
            for (int thread = 0; thread < THREADS; thread++) {
                Class<? extends Component> type = TYPES.get(thread);
                Object expectedFamily = expected.get(thread);
                wrongFamilies.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    int wrong = 0;
                    for (int round = 0; round < ROUNDS_PER_THREAD; round++) {
                        // equals của Family (Ashley) là so danh tính.
                        if (!expectedFamily.equals(Family.all(type).get().raw())) {
                            wrong++;
                        }
                    }
                    return wrong;
                }));
            }
            for (int thread = 0; thread < THREADS; thread++) {
                assertThat(wrongFamilies.get(thread).get(30, TimeUnit.SECONDS))
                        .as("số lần luồng %s nhận Family của tập component KHÁC", thread)
                        .isZero();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
