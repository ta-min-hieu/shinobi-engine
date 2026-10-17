package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;

/** Dòng 71(f) (2026-10-17) — tin chat của một kết nối phải tới đúng thứ tự gửi. */
class SerialExecutorTest {

    @Test
    void shouldRunTasksInSubmissionOrder_evenWhenEachTaskTakesARandomTime() throws InterruptedException {
        ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
        SerialExecutor serial = new SerialExecutor(virtualThreads);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        int count = 200;
        CountDownLatch done = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            int index = i;
            serial.execute(() -> {
                LockSupport.parkNanos(index * 7_919L % 50_000); // thời lượng khác nhau, tất định
                order.add(index);
                done.countDown();
            });
        }

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        virtualThreads.shutdown();
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            expected.add(i);
        }
        assertThat(order).isEqualTo(expected);
    }

    @Test
    void shouldNeverRunTwoTasksAtOnce() throws InterruptedException {
        ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
        SerialExecutor serial = new SerialExecutor(virtualThreads);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            serial.execute(() -> {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                LockSupport.parkNanos(20_000);
                running.decrementAndGet();
                done.countDown();
            });
        }

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        virtualThreads.shutdown();
        assertThat(maxRunning.get()).isEqualTo(1);
    }

    @Test
    void aThrowingTaskShouldNotStallTheTasksBehindIt() {
        List<Runnable> submitted = new ArrayList<>();
        SerialExecutor serial = new SerialExecutor(submitted::add);
        List<String> ran = new ArrayList<>();

        serial.execute(() -> {
            throw new IllegalStateException("boom");
        });
        serial.execute(() -> ran.add("second"));

        assertThatThrownBy(() -> submitted.get(0).run()).isInstanceOf(IllegalStateException.class);
        assertThat(submitted).as("phần còn lại được giao cho một lượt chạy mới").hasSize(2);
        submitted.get(1).run();
        assertThat(ran).containsExactly("second");
    }

    @Test
    void shouldAcceptNewTasksAgain_afterTheDelegateRejectedOne() {
        AtomicInteger calls = new AtomicInteger();
        List<Runnable> submitted = new ArrayList<>();
        SerialExecutor serial = new SerialExecutor(task -> {
            if (calls.getAndIncrement() == 0) {
                throw new RejectedExecutionException("đang tắt");
            }
            submitted.add(task);
        });

        assertThatThrownBy(() -> serial.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
        serial.execute(() -> {});

        assertThat(submitted).as("không kẹt sau cờ draining").hasSize(1);
    }
}
