package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.protocol.MoveResponse;

class LocalPositionReconcilerTest {

    private static PositionComponent at(float x, float y) {
        PositionComponent pos = new PositionComponent();
        pos.x = x;
        pos.y = y;
        pos.prevX = x;
        pos.prevY = y;
        return pos;
    }

    private static MoveResponse serverSays(float x, float y) {
        return MoveResponse.newBuilder()
                .setX(x)
                .setY(y)
                .setZ(0f)
                .setGrounded(true)
                .build();
    }

    @Test
    void shouldKeepPrediction_whenServerAgreesWithinThreshold() {
        PositionComponent pos = at(10f, 0f);

        boolean snapped = LocalPositionReconciler.reconcile(pos, serverSays(10.5f, 0f));

        assertThat(snapped).isFalse();
        assertThat(pos.x).isEqualTo(10f);
    }

    @Test
    void shouldSnapToServer_whenPredictionDriftedFar() {
        PositionComponent pos = at(10f, 0f);

        boolean snapped = LocalPositionReconciler.reconcile(pos, serverSays(20f, 0f));

        assertThat(snapped).isTrue();
        assertThat(pos.x).isEqualTo(20f);
        assertThat(pos.prevX).isEqualTo(20f);
    }

    /**
     * Vị trí dự đoán đã là NaN (một toạ độ hỏng từng lọt từ DB lên gói vào world — đã vá phía server
     * ở 5aab474, nhưng hồi sinh/đổi World là hai đường khác cũng ghi thẳng vào đây). Trước bản vá
     * phép so {@code sqrt(NaN) > ngưỡng} luôn {@code false}, nên KHÔNG MỘT {@code MoveResponse} nào
     * sửa được nữa: camera bám NaN, cả thế giới biến mất tới lần hồi sinh kế tiếp — dù server vẫn
     * đang gửi toạ độ đúng mỗi lần người chơi bấm phím đi.
     */
    @Test
    void shouldSnapToServer_whenLocalPredictionIsNotFinite() {
        PositionComponent pos = at(Float.NaN, 0f);

        boolean snapped = LocalPositionReconciler.reconcile(pos, serverSays(12f, 0f));

        assertThat(snapped).isTrue();
        assertThat(pos.x).isEqualTo(12f);
        assertThat(pos.prevX).isEqualTo(12f);
    }

    /** Toạ độ hỏng TỪ server thì giữ dự đoán đang có — không được chép NaN/vô cực vào camera. */
    @Test
    void shouldIgnoreServerPosition_whenItIsNotFinite() {
        PositionComponent pos = at(10f, 0f);

        boolean snapped = LocalPositionReconciler.reconcile(pos, serverSays(Float.POSITIVE_INFINITY, 0f));

        assertThat(snapped).isFalse();
        assertThat(pos.x).isEqualTo(10f);
    }

    @Test
    void shouldTeleportOnRespawnOrWorldChange() {
        PositionComponent pos = at(10f, 0f);
        pos.z = 2f;
        pos.grounded = false;

        boolean moved = LocalPositionReconciler.snapTo(pos, -3f, 1f);

        assertThat(moved).isTrue();
        assertThat(pos.x).isEqualTo(-3f);
        assertThat(pos.y).isEqualTo(1f);
        assertThat(pos.z).isZero();
        assertThat(pos.grounded).isTrue();
    }

    /**
     * Hồi sinh/đổi World mang toạ độ hỏng: trước bản vá chép thẳng NaN vào vị trí của mình — và vì
     * {@link #shouldSnapToServer_whenLocalPredictionIsNotFinite} cũng hỏng, không gì kéo về được.
     */
    @Test
    void shouldNotTeleportToANonFinitePosition() {
        PositionComponent pos = at(10f, 0f);

        boolean moved = LocalPositionReconciler.snapTo(pos, Float.NaN, 0f);

        assertThat(moved).isFalse();
        assertThat(pos.x).isEqualTo(10f);
        assertThat(pos.prevX).isEqualTo(10f);
    }
}
