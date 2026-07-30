package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Đồng hồ giả — test xác định, không phụ thuộc thời gian thật trôi (không {@code Thread.sleep}). */
class RateLimiterTest {

    private static final class FakeClock {
        private long nanos;

        long asLong() {
            return nanos;
        }

        void advanceSeconds(long seconds) {
            nanos += Duration.ofSeconds(seconds).toNanos();
        }
    }

    @Test
    void shouldAllowUpToLimit_withinSameWindow() {
        FakeClock clock = new FakeClock();
        RateLimiter limiter = new RateLimiter(3, Duration.ofSeconds(1), clock::asLong);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void shouldRejectBeyondLimit_withinSameWindow() {
        FakeClock clock = new FakeClock();
        RateLimiter limiter = new RateLimiter(3, Duration.ofSeconds(1), clock::asLong);
        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();

        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void shouldAllowAgain_afterWindowRotates() {
        FakeClock clock = new FakeClock();
        RateLimiter limiter = new RateLimiter(3, Duration.ofSeconds(1), clock::asLong);
        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertThat(limiter.tryAcquire()).isFalse(); // window đầu hết hạn mức

        clock.advanceSeconds(1);

        assertThat(limiter.tryAcquire()).isTrue(); // window mới, hạn mức reset
    }

    @Test
    void shouldNotSignalDisconnect_forSingleBurst() {
        FakeClock clock = new FakeClock();
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(1), clock::asLong);
        limiter.tryAcquire();
        limiter.tryAcquire(); // vượt ngưỡng đúng 1 window — chỉ là burst ngắn

        assertThat(limiter.shouldDisconnect()).isFalse();
    }

    @Test
    void shouldSignalDisconnect_afterSustainedViolationAcrossManyWindows() {
        FakeClock clock = new FakeClock();
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(1), clock::asLong);

        // Vượt ngưỡng LIÊN TỤC nhiều window — mô phỏng client cố tình spam, không phải 1 burst
        // ngẫu nhiên (NETWORK.md §13: "vượt liên tục → ngắt kết nối").
        for (int window = 0; window < 4; window++) {
            limiter.tryAcquire();
            limiter.tryAcquire(); // vượt ngưỡng ở MỌI window
            clock.advanceSeconds(1);
        }

        assertThat(limiter.shouldDisconnect()).isTrue();
    }

    @Test
    void shouldResetConsecutiveCount_whenAWindowStaysUnderLimit() {
        FakeClock clock = new FakeClock();
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(1), clock::asLong);

        for (int window = 0; window < 4; window++) {
            limiter.tryAcquire();
            limiter.tryAcquire(); // vượt ngưỡng
            clock.advanceSeconds(1);
        }
        // 1 window "sạch" — không vượt ngưỡng — phải làm mất chuỗi liên tục.
        limiter.tryAcquire();
        clock.advanceSeconds(1);

        limiter.tryAcquire();
        limiter.tryAcquire(); // vượt ngưỡng lại, nhưng chuỗi liên tục đã bị ngắt trước đó

        assertThat(limiter.shouldDisconnect()).isFalse();
    }
}
