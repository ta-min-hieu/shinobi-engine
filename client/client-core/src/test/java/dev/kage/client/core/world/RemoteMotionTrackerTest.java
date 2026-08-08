package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RemoteMotionTrackerTest {

    private static final long CHARACTER_ID = 42L;

    @Test
    void isMoving_falseForUnknownCharacter() {
        RemoteMotionTracker tracker = new RemoteMotionTracker();

        assertThat(tracker.isMoving(CHARACTER_ID)).isFalse();
    }

    @Test
    void isMoving_falseOnFirstPositionUpdate() {
        // Lần đầu thấy nhân vật (VD ngay sau CharacterSpawnedEvent) — chưa có gì để so vị trí, nên
        // coi như đứng yên tới gói kế tiếp thay vì suy diễn "đang chạy" từ 1 điểm dữ liệu.
        RemoteMotionTracker tracker = new RemoteMotionTracker();

        tracker.onPositionUpdate(CHARACTER_ID, 5f, 5f);

        assertThat(tracker.isMoving(CHARACTER_ID)).isFalse();
    }

    @Test
    void isMoving_trueAfterRealPositionChangeBetweenTwoUpdates() {
        RemoteMotionTracker tracker = new RemoteMotionTracker();

        tracker.onPositionUpdate(CHARACTER_ID, 5f, 5f);
        tracker.onPositionUpdate(CHARACTER_ID, 5.5f, 5f);

        assertThat(tracker.isMoving(CHARACTER_ID)).isTrue();
    }

    @Test
    void isMoving_falseForSubEpsilonJitterBetweenUpdates() {
        // Sai số float/làm tròn server (VD 2 gói AoI sync liên tiếp cùng vị trí thật) không được
        // coi là di chuyển thật.
        RemoteMotionTracker tracker = new RemoteMotionTracker();

        tracker.onPositionUpdate(CHARACTER_ID, 5f, 5f);
        tracker.onPositionUpdate(CHARACTER_ID, 5.0001f, 5f);

        assertThat(tracker.isMoving(CHARACTER_ID)).isFalse();
    }

    @Test
    void isMoving_becomesFalseAfterTimeoutWithoutNewPositionUpdate() {
        // Server KHÔNG gửi gói "đã dừng" (chỉ gửi CharacterMovedEvent khi vị trí thật đổi) — client
        // phải tự suy ra "đã dừng" từ việc không còn gói mới tới sau một khoảng timeout.
        RemoteMotionTracker tracker = new RemoteMotionTracker();
        tracker.onPositionUpdate(CHARACTER_ID, 5f, 5f);
        tracker.onPositionUpdate(CHARACTER_ID, 5.5f, 5f);
        assertThat(tracker.isMoving(CHARACTER_ID)).isTrue();

        tracker.tick(0.3f); // > STOPPED_TIMEOUT_SECONDS nội bộ (0.2s)

        assertThat(tracker.isMoving(CHARACTER_ID)).isFalse();
    }

    @Test
    void isMoving_staysTrueUnderTimeoutWhileUpdatesKeepArriving() {
        // Mô phỏng AoI sync 20Hz (50ms/gói, server-world.WorldConstants.AOI_SYNC_RATE_HZ) — mỗi
        // gói tới đều reset timeout nên nhân vật vẫn "đang chạy" liên tục khi thực sự đang di chuyển.
        RemoteMotionTracker tracker = new RemoteMotionTracker();
        tracker.onPositionUpdate(CHARACTER_ID, 0f, 0f);

        for (int i = 1; i <= 5; i++) {
            tracker.tick(0.05f);
            tracker.onPositionUpdate(CHARACTER_ID, i * 0.5f, 0f);
            assertThat(tracker.isMoving(CHARACTER_ID)).isTrue();
        }
    }

    @Test
    void remove_clearsState() {
        RemoteMotionTracker tracker = new RemoteMotionTracker();
        tracker.onPositionUpdate(CHARACTER_ID, 5f, 5f);
        tracker.onPositionUpdate(CHARACTER_ID, 5.5f, 5f);
        assertThat(tracker.isMoving(CHARACTER_ID)).isTrue();

        tracker.remove(CHARACTER_ID);

        assertThat(tracker.isMoving(CHARACTER_ID)).isFalse();
    }
}
