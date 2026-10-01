package dev.kage.tools.loadtest.probe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Kiểm sống V50 (2026-10-01): nhát đầu của probe tới trễ ~1,15–1,3 s vì lệnh mở màn ngoài tầm bị
 * server bỏ im lặng rồi probe chờ trọn một nhịp mới thử lại. Đồng hồ ở đây là số giả — không socket.
 */
final class AttackCadenceTest {

    private static final long INTERVAL = 1100L;
    private static final int MAX_READ = 200;

    @Test
    void shouldRetryQuickly_untilTheFirstStrikeIsConfirmed() {
        AttackCadence cadence = new AttackCadence(INTERVAL, 0L);

        assertThat(cadence.isDue(0L, 0)).isTrue();
        cadence.onSent(0L); // lệnh này rơi vì ngoài tầm — server không trả gì

        assertThat(cadence.isDue(AttackCadence.FIRST_STRIKE_RETRY_MILLIS - 1, 0))
                .isFalse();
        assertThat(cadence.isDue(AttackCadence.FIRST_STRIKE_RETRY_MILLIS, 0))
                .as("thử lại sau 100ms, KHÔNG phải sau trọn 1100ms như bản cũ")
                .isTrue();
    }

    @Test
    void shouldFallBackToTheConfiguredInterval_measuredFromWhenTheFirstStrikeWasSeen() {
        AttackCadence cadence = new AttackCadence(INTERVAL, 0L);
        cadence.onSent(0L);
        cadence.onSent(100L); // lần thử lại này trúng
        // DamageEvent của chính mình tới lúc 130ms.
        assertThat(cadence.isDue(130L, 1)).isFalse();
        // Lần thử lại dư (nếu có) không được kéo nhịp sớm hơn: hạn kế tiếp tính từ lúc thấy nhát đầu.
        assertThat(cadence.isDue(130L + INTERVAL - 1, 1)).isFalse();
        assertThat(cadence.isDue(130L + INTERVAL, 1)).isTrue();

        cadence.onSent(130L + INTERVAL);
        assertThat(cadence.isDue(130L + (2 * INTERVAL) - 1, 2)).isFalse();
        assertThat(cadence.isDue(130L + (2 * INTERVAL), 2)).isTrue();
    }

    @Test
    void shouldNeverWaitPastTheNextSend_soTheIntervalIsNotStretchedByTheReadWindow() {
        AttackCadence cadence = new AttackCadence(INTERVAL, 0L);
        cadence.isDue(0L, 1);
        cadence.onSent(0L);

        assertThat(cadence.readTimeoutMillis(0L, MAX_READ)).isEqualTo(MAX_READ);
        assertThat(cadence.readTimeoutMillis(INTERVAL - 50, MAX_READ)).isEqualTo(50);
        assertThat(cadence.readTimeoutMillis(INTERVAL + 500, MAX_READ))
                .as("quá hạn vẫn có sàn — hết giờ giữa frame làm lệch dòng byte")
                .isEqualTo((int) AttackCadence.MIN_READ_TIMEOUT_MILLIS);
    }
}
