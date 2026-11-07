package dev.kage.client.core.world;

/**
 * Đích tự đi (ADR-206) — một toạ độ X ở world chính mà {@link LocalIntentSender} lái nhân vật tới thay cho phím. Chỉ
 * là ý định của client: server vẫn nhận hướng đi -1/0/1 như khi người chơi tự bấm phím, không có lệnh "dịch chuyển".
 *
 * <p>Ghi bởi {@link AutoWalkCoordinator} (luồng render, lúc nhận gói) và đọc bởi {@link LocalIntentSender} (tick cố
 * định) — cùng một luồng libGDX, không cần đồng bộ.
 */
final class AutoWalkTarget {

    /** Đứng cách đích không quá chừng này là tới nơi — trong tầm nói chuyện NPC (5 m) với khoảng dư. */
    static final float ARRIVE_DISTANCE_METERS = 2.5f;

    private boolean active;
    private float targetX;
    private float lastKnownX;
    private Runnable onArrived = AutoWalkTarget::doNothing;
    private Runnable onStopped = AutoWalkTarget::doNothing;

    /** Bắt đầu đi tới {@code x}; {@code arrived}/{@code stopped} chạy đúng một lần khi tới nơi / bị huỷ. */
    void start(float x, Runnable arrived, Runnable stopped) {
        targetX = x;
        onArrived = arrived;
        onStopped = stopped;
        active = true;
    }

    boolean isActive() {
        return active;
    }

    float targetX() {
        return targetX;
    }

    /** Vị trí X gần nhất của chính mình — để chọn chỗ đứng gần nhất khi một NPC đứng nhiều nơi. */
    float lastKnownX() {
        return lastKnownX;
    }

    void trackPosition(float x) {
        lastKnownX = x;
    }

    void arrive() {
        finish(onArrived);
    }

    /** Huỷ (bấm phím đi, đổi World, bị chặn đường); không làm gì nếu đang không tự đi. */
    void stop() {
        finish(onStopped);
    }

    private void finish(Runnable callback) {
        if (!active) {
            return;
        }
        active = false;
        onArrived = AutoWalkTarget::doNothing;
        onStopped = AutoWalkTarget::doNothing;
        callback.run();
    }

    private static void doNothing() {
        // đích đã xong: không còn ai chờ tin
    }
}
