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

    @Test
    void shouldSaySomething_onlyAtTheEdgeOfAConnectionChange() {
        // Nhịp ĐẦU TIÊN im lặng: `previous == null` là "chưa đo lần nào", không phải "vừa nối lại".
        // Thiếu ô này thì mỗi lần vào world lại hiện một dòng "đã kết nối lại" vô nghĩa.
        assertThat(ConnectionCoordinator.noticeKeyFor(null, true)).isEmpty();
        assertThat(ConnectionCoordinator.noticeKeyFor(null, false)).isEmpty();

        // Đứng yên thì im — hàm này chạy MỖI TICK (60 lần/giây).
        assertThat(ConnectionCoordinator.noticeKeyFor(true, true)).isEmpty();
        assertThat(ConnectionCoordinator.noticeKeyFor(false, false)).isEmpty();

        assertThat(ConnectionCoordinator.noticeKeyFor(true, false)).hasValue("system.notice.connection_lost");
        assertThat(ConnectionCoordinator.noticeKeyFor(false, true)).hasValue("system.notice.reconnected");
    }

    /**
     * Một nhân vật chỉ một phiên sống (quyết định chủ dự án 2026-09-25): server đá phiên cũ bằng
     * {@code error.logged_in_elsewhere} rồi đóng kết nối. Chỉ đúng khoá đó mới kết thúc phiên — mọi
     * lỗi khác vẫn là "một thao tác bị từ chối", kể cả {@code error.not_authenticated}.
     */
    @Test
    void shouldEndTheSession_onlyWhenAnotherConnectionTookTheCharacterOver() {
        assertThat(ConnectionCoordinator.endsSession("error.logged_in_elsewhere"))
                .isTrue();

        assertThat(ConnectionCoordinator.endsSession("error.not_authenticated")).isFalse();
        assertThat(ConnectionCoordinator.endsSession("error.rate_limited")).isFalse();
        assertThat(ConnectionCoordinator.endsSession("")).isFalse();
        assertThat(ConnectionCoordinator.endsSession(null)).isFalse();
    }
}
