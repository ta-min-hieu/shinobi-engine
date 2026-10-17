package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Dòng 78(b): tắt server quá trần chờ thì lượt trao thưởng còn dở bị cắt (chấp nhận) — nhưng dispatcher
 * còn biết đúng lượt nào để WARN liệt kê ra mà bù tay.
 */
@Timeout(10)
class KillRewardDispatcherShutdownTest {

    private static final long SHORT_DRAIN_MILLIS = 100L;

    @Test
    void shouldStillKnowTheCutTask_whenStopTimesOut() throws InterruptedException {
        KillRewardDispatcher dispatcher = new KillRewardDispatcher();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        dispatcher.dispatch(KillRewardDispatcher.Step.EXPERIENCE, 7L, "wolf", () -> {
            started.countDown();
            awaitQuietly(release);
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        dispatcher.stop(SHORT_DRAIN_MILLIS);

        assertThat(dispatcher.pendingCount()).isEqualTo(1);
        release.countDown();
    }

    @Test
    void shouldForgetFinishedTasks() {
        KillRewardDispatcher dispatcher = new KillRewardDispatcher();
        dispatcher.dispatch(KillRewardDispatcher.Step.LOOT, 7L, "wolf", () -> {});
        dispatcher.dispatch(KillRewardDispatcher.Step.QUEST_PROGRESS, 7L, "wolf", () -> {
            throw new IllegalStateException("DB chập chờn (giả lập)");
        });

        dispatcher.stop(SHORT_DRAIN_MILLIS * 10);

        assertThat(dispatcher.pendingCount()).isZero();
    }

    @Test
    void shouldNotTrackTasksRejectedAfterStop() {
        KillRewardDispatcher dispatcher = new KillRewardDispatcher();
        dispatcher.stop(SHORT_DRAIN_MILLIS);

        dispatcher.dispatch(KillRewardDispatcher.Step.LOOT, 7L, "wolf", () -> {});

        assertThat(dispatcher.pendingCount()).isZero();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
