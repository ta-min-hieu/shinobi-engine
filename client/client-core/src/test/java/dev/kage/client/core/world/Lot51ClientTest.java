package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.FriendView;

/**
 * Lô 51 phía client — lời nhắn trong danh sách bạn (ADR-335), màu kênh (ADR-339), rung (ADR-342), đầy máu (ADR-340).
 */
class Lot51ClientTest {

    @Test
    void friendNameShowsStatus() {
        FriendView view = FriendView.newBuilder()
                .setName("BaoDo01")
                .setNote("bạn cũ")
                .setStatusMessage("Bế quan")
                .build();
        assertThat(FriendController.displayNameOf(view)).isEqualTo("BaoDo01 (bạn cũ) — Bế quan");
        assertThat(FriendController.displayNameOf(
                        view.toBuilder().clearStatusMessage().build()))
                .isEqualTo("BaoDo01 (bạn cũ)");
    }

    @Test
    void parsesChatColors() {
        assertThat(ChatColorCoordinator.parseColor("do")).contains("FF5A4A");
        assertThat(ChatColorCoordinator.parseColor("#a1b2c3")).contains("A1B2C3");
        assertThat(ChatColorCoordinator.parseColor("zzzzzz")).isEmpty();
        assertThat(ChatColorCoordinator.parseColor("12345")).isEmpty();
    }

    @Test
    void heavyHitThreshold() {
        assertThat(ScreenShakeCoordinator.isHeavy(10, 100)).isTrue();
        assertThat(ScreenShakeCoordinator.isHeavy(9, 100)).isFalse();
        assertThat(ScreenShakeCoordinator.isHeavy(50, 0)).isFalse();
    }

    @Test
    void fullHealthNotifiesOncePerRecovery() {
        FullHealthCoordinator.Tracker full = new FullHealthCoordinator.Tracker();
        assertThat(full.onHealth(100, 100)).isFalse();
        assertThat(full.onHealth(60, 100)).isFalse();
        assertThat(full.onHealth(100, 100)).isTrue();
        assertThat(full.onHealth(100, 100)).isFalse();
    }
}
