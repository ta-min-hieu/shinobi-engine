package dev.kage.client.core.world;

import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.protocol.MoveResponse;

/**
 * Đối chiếu vị trí DỰ ĐOÁN của chính mình với vị trí server nói (CLIENT.md §6) — và các cú dịch
 * chuyển tức thời do server quyết (hồi sinh, đổi World ADR-051).
 *
 * <p>Tách khỏi {@link WorldScreen} để test được không cần context GL: lớp đó dựng
 * {@code ShapeRenderer}/{@code ModelBatch} ngay trong constructor.
 */
final class LocalPositionReconciler {

    private LocalPositionReconciler() {}

    /**
     * Sửa vị trí dự đoán theo {@code MoveResponse} nếu lệch quá
     * {@link WorldConstants#RECONCILE_SNAP_THRESHOLD}.
     *
     * @return {@code true} nếu đã snap theo server
     */
    static boolean reconcile(PositionComponent pos, MoveResponse response) {
        if (!isFinite(response.getX(), response.getY()) || !Float.isFinite(response.getZ())) {
            // Toạ độ hỏng TỪ server: giữ dự đoán đang có — chép NaN/vô cực vào đây là kéo camera
            // theo, cả thế giới biến mất khỏi màn hình.
            return false;
        }
        float dx = response.getX() - pos.x;
        float dy = response.getY() - pos.y;
        float dz = response.getZ() - pos.z;
        double drift = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        // `!(drift <= ngưỡng)` chứ không phải `drift > ngưỡng`: vị trí dự đoán đã NaN thì drift là
        // NaN, mọi phép so đều false — viết kiểu sau thì KHÔNG một MoveResponse nào sửa được nữa.
        if (!(drift <= WorldConstants.RECONCILE_SNAP_THRESHOLD)) {
            // Lệch lớn (mất gói MoveCommand/JumpCommand trước đó, hoặc server điều chỉnh) — sửa
            // NGAY theo server, không tranh cãi (Điều 15, CLIENT.md §6).
            pos.x = response.getX();
            pos.y = response.getY();
            pos.prevX = pos.x;
            pos.prevY = pos.y;
            pos.z = response.getZ();
            pos.prevZ = pos.z;
            pos.grounded = response.getGrounded();
            if (pos.grounded) {
                // Server nói đang đứng đất ⇒ hạn mức nhảy đôi bên server CHẮC CHẮN đã reset
                // (VerticalMovementSystem xoá lúc đáp đất) — theo server luôn, tránh trường hợp
                // client tưởng còn nợ 1 lần nhảy giữa không trung dù đã đứng trên đất.
                pos.airJumpsUsed = 0;
            }
            // vz KHÔNG snap theo server — MoveResponse không mang vz (chỉ x/y/z/grounded, xem
            // movement.proto). Dự đoán cục bộ tiếp tục dùng vz hiện có, tự hội tụ đúng quỹ đạo ở
            // các tick sau vì VerticalMovementSystem chạy CÙNG công thức cả 2 phía (ADR-033) —
            // follow-up nếu số liệu thật cho thấy cần đồng bộ cả vz.
            return true;
        }
        return false;
    }

    /**
     * Đặt chính mình tới {@code (x, y)} trên mặt đất NGAY, không nội suy — hồi sinh và đổi World
     * (ADR-051) đều là dịch chuyển tức thời. ADR-033 — reset cả trục dọc: có thể chết/đổi World giữa
     * lúc đang lơ lửng.
     *
     * <p>Toạ độ không hữu hạn thì KHÔNG dời: đứng nguyên chỗ cũ còn chơi tiếp được (và
     * {@code MoveResponse} kế tiếp sẽ kéo về đúng chỗ), còn NaN trong vị trí là mất cả màn hình.
     *
     * @return {@code true} nếu đã dời, {@code false} nếu bỏ qua vì toạ độ hỏng
     */
    static boolean snapTo(PositionComponent pos, float x, float y) {
        if (!isFinite(x, y)) {
            return false;
        }
        pos.x = x;
        pos.y = y;
        pos.prevX = pos.x;
        pos.prevY = pos.y;
        pos.z = 0f;
        pos.prevZ = 0f;
        pos.vz = 0f;
        pos.grounded = true;
        pos.airJumpsUsed = 0;
        return true;
    }

    private static boolean isFinite(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y);
    }
}
