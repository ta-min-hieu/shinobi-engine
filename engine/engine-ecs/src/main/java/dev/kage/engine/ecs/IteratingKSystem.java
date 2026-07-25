package dev.kage.engine.ecs;

/**
 * Base class cho System xử lý mọi Entity thuộc một Family, mỗi tick (ECS.md §4).
 *
 * <p>⚠️ PHÁT HIỆN THẬT (Phase 4): KHÔNG đăng ký qua {@code Engine.addSystem(EntitySystem)} của
 * Ashley — {@code Engine.addSystem} coi 2 system CÙNG {@code getClass()} là "cùng 1 system, thay
 * thế cái cũ". Nếu mỗi {@code IteratingKSystem} bọc qua 1 lớp adapter DÙNG CHUNG (như bản trước
 * dùng), Ashley thấy TẤT CẢ adapter là "cùng class" → đăng ký system thứ 2 ÂM THẦM GỠ system thứ
 * nhất khỏi engine (không lỗi, không log — chỉ đơn giản dừng chạy). Lỗi này VÔ HÌNH suốt Phase
 * 1-3 vì chỉ bao giờ có ĐÚNG 1 System (MovementSystem) — Phase 4 là lần đầu có 2 System cùng lúc
 * (MovementSystem + CombatSystem) mới lộ ra.
 *
 * <p>{@link EcsWorld} KHÔNG dùng {@code Engine.addSystem}/{@code Engine.update} của Ashley cho
 * các System này nữa — tự lặp qua {@code Engine.getEntitiesFor(Family)} (không phụ thuộc
 * {@code getClass()}, an toàn với bao nhiêu System cũng được) và gọi thẳng {@link #processEntity}.
 *
 * <pre>{@code
 * public final class MovementSystem extends IteratingKSystem {
 *     private static final ComponentMapper<PositionComponent> POS = ComponentMapper.getFor(PositionComponent.class);
 *     private static final ComponentMapper<VelocityComponent> VEL = ComponentMapper.getFor(VelocityComponent.class);
 *
 *     public MovementSystem() {
 *         super(Family.all(PositionComponent.class, VelocityComponent.class).get());
 *     }
 *
 *     protected void processEntity(KEntity entity, float deltaTime) {
 *         PositionComponent pos = POS.get(entity);
 *         VelocityComponent vel = VEL.get(entity);
 *         pos.x += vel.x * deltaTime;
 *         pos.y += vel.y * deltaTime;
 *     }
 * }
 * }</pre>
 */
public abstract class IteratingKSystem {

    private final Family family;

    protected IteratingKSystem(Family family) {
        this.family = family;
    }

    /**
     * System không giữ state của entity — state ở Component (ECS.md §4). Cần gì từ
     * {@code EcsWorld}/nơi khác thì nhận qua constructor injection tường minh (engine/CLAUDE.md
     * — cấm kiểu Service Locator), không phải tra ngược từ System.
     */
    protected abstract void processEntity(KEntity entity, float deltaTime);

    /** Chỉ EcsWorld cần Family gốc để tự lặp qua {@code Engine.getEntitiesFor} mỗi tick. */
    Family family() {
        return family;
    }
}
