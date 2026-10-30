package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingRevivalsTest {

    private final PendingRevivals revivals = new PendingRevivals();
    private final List<Long> expired = new ArrayList<>();

    @Test
    @DisplayName("Hết thời gian chờ ⇒ tự về điểm hồi sinh đúng một lần")
    void expiresExactlyOnceAfterTheWait() {
        revivals.begin(7L, false);

        revivals.tick(PendingRevivals.WAIT_SECONDS - 0.5f, expired::add);
        assertThat(expired).isEmpty();
        revivals.tick(1f, expired::add);
        revivals.tick(1f, expired::add);

        assertThat(expired).containsExactly(7L);
        assertThat(revivals.isPending(7L)).isFalse();
    }

    @Test
    @DisplayName("Bị người chơi hạ ⇒ không đứng dậy tại chỗ; bị quái hạ thì được")
    void playerKillsForbidInPlaceRevival() {
        revivals.begin(1L, true);
        revivals.begin(2L, false);

        assertThat(revivals.canReviveInPlace(1L)).isFalse();
        assertThat(revivals.canReviveInPlace(2L)).isTrue();
        assertThat(revivals.canReviveInPlace(3L)).isFalse();
    }

    @Test
    @DisplayName("Huỷ (đã chọn, rời World) ⇒ không còn tự hồi sinh nữa")
    void cancelStopsTheCountdown() {
        revivals.begin(7L, false);
        revivals.cancel(7L);

        revivals.tick(PendingRevivals.WAIT_SECONDS + 1f, expired::add);

        assertThat(expired).isEmpty();
    }

    @Test
    @DisplayName("Đứng dậy tại chỗ với 30% máu, ít nhất 1")
    void inPlaceHpIsThirtyPercentAtLeastOne() {
        assertThat(PendingRevivals.inPlaceHp(500)).isEqualTo(150);
        assertThat(PendingRevivals.inPlaceHp(1)).isEqualTo(1);
    }
}
