package dev.kage.client.core.world;

/**
 * Ánh xạ trạng thái gameplay quan sát được ở client → tên clip animation
 * ([RENDERING.md §11.1](docs/03-engine/RENDERING.md)). Thuần hàm, không phụ thuộc libGDX/GL —
 * {@link CharacterModelRenderer} là nơi DUY NHẤT áp kết quả vào {@code AnimationController} thật,
 * nên lớp này test được thẳng không cần context đồ hoạ.
 *
 * <p>Bộ 7 clip chuẩn theo [ART-PIPELINE §4.2](docs/14-art/ART-PIPELINE.md), xuất từ đúng 1 sidecar
 * {@code .model-meta.json} cho mỗi model. {@code jump} cố ý KHÔNG có hằng số ở đây:
 * {@link dev.kage.client.core.input.GameAction} chưa có ý định nhảy nào — tự thêm input gameplay
 * mới ở tầng render là việc AI không được tự quyết ([/CLAUDE.md §4](../../../../../../../CLAUDE.md)).
 */
final class CharacterAnimationClips {

    static final String IDLE = "idle";
    static final String RUN = "run";
    static final String ATTACK = "attack";
    static final String HIT = "hit";
    static final String DIE = "die";
    static final String SKILL_CAST = "skill_cast";

    /**
     * Vận tốc dưới ngưỡng này (world unit/giây) coi là đứng yên. Cần một ngưỡng thay vì so sánh
     * với 0 tuyệt đối vì {@code VelocityComponent} có thể mang giá trị rất nhỏ do sai số float
     * (chuẩn hoá vector hướng) mà không thực sự là "đang di chuyển" — so 0 tuyệt đối sẽ khiến
     * idle/run nhấp nháy qua lại mỗi frame.
     */
    private static final float MOVING_SPEED_EPSILON = 0.05f;

    private static final float MOVING_SPEED_EPSILON_SQUARED = MOVING_SPEED_EPSILON * MOVING_SPEED_EPSILON;

    private CharacterAnimationClips() {}

    /**
     * True nếu vận tốc đủ lớn để coi là đang di chuyển (idle vs run). Dùng bình phương độ dài để
     * tránh {@code Math.sqrt} mỗi lần gọi — so sánh ngưỡng bình phương cho kết quả y hệt.
     */
    static boolean isMoving(float velocityX, float velocityY) {
        return ((velocityX * velocityX) + (velocityY * velocityY)) > MOVING_SPEED_EPSILON_SQUARED;
    }

    /** Clip loop tương ứng — {@link #RUN} khi đang di chuyển, ngược lại {@link #IDLE}. */
    static String loopingClipFor(boolean moving) {
        return moving ? RUN : IDLE;
    }
}
