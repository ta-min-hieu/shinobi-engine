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

    /**
     * Bẫy: {@code violatedThisWindow} chỉ được "chốt" khi có {@code tryAcquire()} gọi SAU khi window
     * đã hết hạn — nếu client im lặng xuyên qua NHIỀU window liên tiếp (không gọi gì cả) rồi mới gọi
     * lại, window đang treo ({@code violatedThisWindow}) thuộc về một window đã cách đây RẤT LÂU, không
     * còn kề (liên tục) với window mới. Trước bản vá, {@code rotateWindowIfElapsed} không phân biệt
     * "vừa đúng 1 window trôi qua" với "n window trôi qua trong im lặng", nên cờ vi phạm cũ bị tính
     * nhầm thành "window liền trước vừa vi phạm" — biến 1 vi phạm CŨ + 2 vi phạm THẬT SỰ liên tục
     * thành đủ 3 "liên tục" để ngắt kết nối, dù chưa từng có 3 window liên tiếp nào thực sự vi phạm.
     */
    @Test
    void shouldNotSignalDisconnect_whenAnOldViolationIsSeparatedByASilentGapFromOnlyTwoRealConsecutiveViolations() {
        FakeClock clock = new FakeClock();
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(1), clock::asLong);

        // Window A: vượt ngưỡng đúng 1 lần — có thể chỉ là một lag spike đơn lẻ, cách đây rất lâu.
        limiter.tryAcquire();
        limiter.tryAcquire();

        // Im lặng NHIỀU window liên tiếp — không một tryAcquire() nào rơi vào khoảng này, nên
        // không window nào trong khoảng lặng này có thể coi là "đã vi phạm".
        clock.advanceSeconds(5);

        // Đúng HAI window liên tục (kề nhau thật) vi phạm sau khoảng lặng.
        limiter.tryAcquire();
        limiter.tryAcquire(); // window B vượt ngưỡng
        clock.advanceSeconds(1);
        limiter.tryAcquire();
        limiter.tryAcquire(); // window C vượt ngưỡng, kề ngay sau B
        clock.advanceSeconds(1);
        limiter.tryAcquire(); // chốt window C vào bộ đếm liên tục (window D bắt đầu)

        // Chỉ 2 window LIÊN TỤC THẬT SỰ vi phạm (B rồi C) — chưa đủ 3, không được ngắt kết nối.
        assertThat(limiter.shouldDisconnect()).isFalse();
    }

    @Test
    void shouldStillSignalDisconnect_afterThreeGenuinelyConsecutiveViolationsFollowingASilentGap() {
        FakeClock clock = new FakeClock();
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(1), clock::asLong);

        limiter.tryAcquire();
        limiter.tryAcquire(); // vi phạm cũ, cách xa

        clock.advanceSeconds(5); // im lặng dài

        for (int window = 0; window < 3; window++) {
            limiter.tryAcquire();
            limiter.tryAcquire(); // vượt ngưỡng ở CẢ BA window liên tục thật sự
            clock.advanceSeconds(1);
        }
        limiter.tryAcquire(); // chốt window thứ ba vào bộ đếm

        assertThat(limiter.shouldDisconnect()).isTrue();
    }
}
