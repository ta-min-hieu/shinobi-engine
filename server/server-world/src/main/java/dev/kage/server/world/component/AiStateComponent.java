package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Trạng thái FSM (AI.md §3) + dữ liệu cần để chuyển trạng thái. Mục tiêu chiến đấu KHÔNG lặp lại
 * ở đây — {@code AiSystem} đọc/ghi {@link TargetComponent} đã có sẵn (quái cũng dùng chung
 * component đó với nhân vật, để {@code CombatSystem} tự động xử lý đòn đánh của quái không cần
 * sửa gì thêm).
 *
 * <p>Mutable + field public: ngoại lệ có chủ đích của Điều 43 (ECS.md §3.1).
 */
public final class AiStateComponent implements Component {
    public AiState state = AiState.IDLE;
    public float stateTimer;
    public float spawnX;
    public float spawnY;
    public float patrolTargetX;
    public float patrolTargetY;
}
