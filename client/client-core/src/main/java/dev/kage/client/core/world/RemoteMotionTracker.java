package dev.kage.client.core.world;

import com.badlogic.gdx.utils.LongMap;

/**
 * Suy ra "đang di chuyển" cho nhân vật KHÁC từ độ lệch vị trí giữa 2 {@code CharacterMovedEvent}
 * liên tiếp — giao thức KHÔNG có field vận tốc cho nhân vật khác (chỉ nhân vật của mình có
 * {@code VelocityComponent}, xem {@link WorldScreen}), nên đây là nguồn duy nhất để chọn clip
 * idle/run của họ ([RENDERING.md §11.1](docs/03-engine/RENDERING.md)).
 *
 * <p>Server chỉ phát {@code CharacterMovedEvent} khi vị trí THẬT ĐỔI
 * ({@code AreaOfInterestTracker.sync}, 20Hz — xem {@code server-world.WorldConstants.AOI_SYNC_RATE_HZ})
 * — KHÔNG có gói "đã dừng lại". Nếu chỉ dựa vào gói tới thì nhân vật dừng lại sẽ đứng ở clip chạy
 * MÃI MÃI vì không còn gói nào tới để sửa nó về idle. {@link #STOPPED_TIMEOUT_SECONDS} là khoảng
 * chờ trước khi tự coi là đã dừng nếu không thấy gói mới — lớn hơn hẳn chu kỳ AoI sync 50ms để
 * chịu được rung nhẹ (jitter mạng) mà vẫn phản hồi đủ nhanh để không "trượt" cảm giác.
 *
 * <p>Thuần Java, không phụ thuộc libGDX/GL — {@link #tick} nhận {@code deltaTime} làm tham số thay
 * vì tự đọc đồng hồ thật, nên test được thẳng và tất định (deterministic).
 */
final class RemoteMotionTracker {

    private static final float STOPPED_TIMEOUT_SECONDS = 0.2f;

    /** 0.01 world unit — dưới mức này coi là rung số float, không phải di chuyển thật. */
    private static final float MOVED_DISTANCE_SQUARED_EPSILON = 0.0001f;

    /** Bộ đếm mutable riêng từng nhân vật — tránh autoboxing {@code Float} mỗi lần {@link #tick}. */
    private static final class MotionTimer {
        private float secondsSinceLastMove = STOPPED_TIMEOUT_SECONDS;
        private float lastX;
        private float lastY;
        private boolean hasPosition;
    }

    /**
     * {@link LongMap}: {@link #isMoving} chạy cho mọi thực thể MỖI KHUNG — tra {@code HashMap} khoá
     * {@code Long} là boxing mỗi lần (đo ở FrameAllocationTest). Lặp {@link LongMap#values()} trả lại
     * cùng một iterator dựng sẵn, không cấp phát.
     */
    private final LongMap<MotionTimer> timersByCharacter = new LongMap<>();

    /** Gọi mỗi khi nhận {@code CharacterMovedEvent} cho nhân vật khác. */
    void onPositionUpdate(long characterId, float newX, float newY) {
        MotionTimer timer = timersByCharacter.get(characterId);
        if (timer == null) {
            timer = new MotionTimer();
            timersByCharacter.put(characterId, timer);
        }
        if (timer.hasPosition) {
            float dx = newX - timer.lastX;
            float dy = newY - timer.lastY;
            if (((dx * dx) + (dy * dy)) > MOVED_DISTANCE_SQUARED_EPSILON) {
                timer.secondsSinceLastMove = 0f;
            }
        }
        timer.lastX = newX;
        timer.lastY = newY;
        timer.hasPosition = true;
    }

    /** Gọi mỗi {@code fixedUpdate} để đếm ngược timeout — cùng đồng hồ fixed-timestep với ECS. */
    void tick(float fixedDeltaTime) {
        for (MotionTimer timer : timersByCharacter.values()) {
            timer.secondsSinceLastMove += fixedDeltaTime;
        }
    }

    boolean isMoving(long characterId) {
        MotionTimer timer = timersByCharacter.get(characterId);
        return timer != null && timer.secondsSinceLastMove < STOPPED_TIMEOUT_SECONDS;
    }

    /** Gọi khi nhân vật rời AoI ({@code CharacterDespawnedEvent}) — tránh rò rỉ map theo phiên chơi. */
    void remove(long characterId) {
        timersByCharacter.remove(characterId);
    }
}
