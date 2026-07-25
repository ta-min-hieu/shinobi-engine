package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Đánh dấu entity là BOSS + giữ trạng thái CƠ CHẾ (Điều 27 — không phải nội dung, nội dung nằm ở
 * script GraalJS, xem {@code BossAiSystem}) cần cho quyết định "dùng chiêu đặc biệt ngay bây giờ
 * không". Boss vẫn có đủ {@link AiStateComponent}/{@link TargetComponent}/
 * {@link AttackCooldownComponent} như quái thường — component này CHỈ cộng thêm, không thay thế.
 *
 * <p>Mutable + field public: ngoại lệ có chủ đích của Điều 43 (ECS.md §3.1).
 */
public final class BossAiComponent implements Component {
    public float decisionCooldownSeconds;
    public float heavyStrikeCooldownSeconds;
    public boolean enraged;
}
