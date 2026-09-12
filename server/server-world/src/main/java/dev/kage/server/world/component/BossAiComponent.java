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

    /**
     * Đường dẫn classpath tới script AI của CHÍNH con này (ADR-048 D3) — chép từ
     * {@code monster_template.ai_script_ref} lúc spawn.
     *
     * <p>Trước ADR-048, {@code BossAiSystem} giữ một chuỗi script duy nhất nạp lúc khởi tạo, nên
     * boss thứ hai không có chỗ nào khai hành vi riêng. Để ref ở ĐÂY (trên từng entity) thay vì
     * trong system là thứ làm cho "mỗi boss một script" thành khả thi mà Java không cần biết boss
     * nào tồn tại.
     */
    public String scriptRef;

    /** Nhịp HỎI script — throttle, không phải mỗi tick (AI.md §9). Từ cột `ai_decision_interval_seconds`. */
    public float decisionIntervalSeconds;

    /** Hồi chiêu đặc biệt. Từ cột `ai_skill_cooldown_seconds`. */
    public float skillCooldownSeconds;

    public float decisionCooldownSeconds;
    public float heavyStrikeCooldownSeconds;
    public boolean enraged;
}
