package dev.kage.client.core.world;

/**
 * Ánh xạ trạng thái gameplay quan sát được ở client → tên clip animation
 * ([RENDERING.md §11.1](docs/03-engine/RENDERING.md)). Thuần hàm, không phụ thuộc libGDX/GL —
 * {@link WorldModelRenderer} là nơi DUY NHẤT áp kết quả vào {@code AnimationController} thật,
 * nên lớp này test được thẳng không cần context đồ hoạ.
 *
 * <p>Bộ 7 clip chuẩn theo [ART-PIPELINE §4.2](docs/14-art/ART-PIPELINE.md), xuất từ đúng 1 sidecar
 * {@code .model-meta.json} cho mỗi model. {@code jump} giờ ĐÃ dùng (ADR-033, 2026-08-08) —
 * {@link dev.kage.client.core.input.GameAction#JUMP} là ý định nhảy chính thức, gỡ đúng điều kiện
 * chặn ghi ở bản trước đó của javadoc này.
 */
final class CharacterAnimationClips {

    static final String IDLE = "idle";
    static final String RUN = "run";
    static final String JUMP = "jump";
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

    /**
     * Clip loop tương ứng — {@link #JUMP} có ƯU TIÊN CAO NHẤT khi đang lơ lửng (ADR-033, bất kể
     * {@code moving}: đang nhảy ngang vẫn phát {@code jump}, không phải {@code run}), ngược lại
     * {@link #RUN} khi đang di chuyển, {@link #IDLE} khi đứng yên trên đất.
     */
    static String loopingClipFor(boolean airborne, boolean moving) {
        if (airborne) {
            return JUMP;
        }
        return moving ? RUN : IDLE;
    }
}
