package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Chỉ phần THUẦN của {@link ConnectionCoordinator} — {@code CombatHud} cần ngữ cảnh GL nên bản thân
 * lớp kia không dựng được trong JUnit, xem javadoc {@link ConnectionCoordinator#plausibleRoundTrip}.
 */
class ConnectionCoordinatorTest {

    @Test
    @DisplayName("Round-trip bình thường là hiệu số hai dấu thời gian của CÙNG một đồng hồ")
    void computesRoundTrip() {
        assertThat(ConnectionCoordinator.plausibleRoundTrip(1_000L, 1_042L)).hasValue(42L);
    }

    @Test
    @DisplayName("Round-trip 0ms (máy chủ chạy cùng máy) vẫn là con số THẬT, không bị loại")
    void keepsZero() {
        assertThat(ConnectionCoordinator.plausibleRoundTrip(1_000L, 1_000L)).hasValue(0L);
    }

    @Test
    @DisplayName("Dấu thời gian ở TƯƠNG LAI (đồng hồ vừa bị chỉnh) bị bỏ, không hiện số âm")
    void dropsNegative() {
        assertThat(ConnectionCoordinator.plausibleRoundTrip(2_000L, 1_000L)).isEmpty();
    }

    @Test
    @DisplayName("Đúng trần vẫn nhận; quá trần một mili là gói lạc của phiên cũ, bị bỏ")
    void dropsAboveCeiling() {
        long ceiling = ConnectionCoordinator.MAX_PLAUSIBLE_ROUND_TRIP_MS;
        assertThat(ConnectionCoordinator.plausibleRoundTrip(0L, ceiling)).hasValue(ceiling);
        assertThat(ConnectionCoordinator.plausibleRoundTrip(0L, ceiling + 1L)).isEmpty();
    }
}
