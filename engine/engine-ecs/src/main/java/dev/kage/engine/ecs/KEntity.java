package dev.kage.engine.ecs;

/**
 * Entity — chỉ là ID (ECS.md §2). Wrapper mỏng quanh Ashley Entity bằng composition
 * (không kế thừa) — gameplay code không bao giờ thấy kiểu {@code com.badlogic.ashley.core.Entity}.
 *
 * <p>⚠️ Ashley (từ ~1.4 trở đi) KHÔNG expose ID số nguyên trên {@code Entity} nữa (khác bản rất
 * cũ) — {@link EcsWorld} tự cấp {@code id} tăng dần lúc {@code createEntity()}, độc lập với
 * Ashley. Đây là ID ổn định của Kage, không phải index nội bộ của Ashley.
 *
 * <p>Không giữ tham chiếu {@code KEntity} lâu dài xuyên nhiều tick nếu entity có thể bị huỷ giữa
 * chừng — luôn tra cứu lại theo ID khi cần (ECS.md §2, tránh tham chiếu treo).
 */
public final class KEntity {

    private final com.badlogic.ashley.core.Entity ashleyEntity;
    private final long id;

    KEntity(com.badlogic.ashley.core.Entity ashleyEntity, long id) {
        this.ashleyEntity = ashleyEntity;
        this.id = id;
    }

    /** Chỉ package này (ComponentMapper, EcsWorld, KSystem) được lấy Entity gốc của Ashley. */
    com.badlogic.ashley.core.Entity raw() {
        return ashleyEntity;
    }

    public long id() {
        return id;
    }

    public <T extends Component> KEntity add(T component) {
        ashleyEntity.add(component);
        return this;
    }

    public boolean has(Class<? extends Component> type) {
        return ashleyEntity.getComponent(type) != null;
    }
}
