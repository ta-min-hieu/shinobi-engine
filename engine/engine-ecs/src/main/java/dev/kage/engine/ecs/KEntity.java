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
 *
 * <p>Handle của entity đã gỡ (hoặc có từ trước {@link EcsWorld#clear}) là handle CHẾT: nó trông như
 * một entity rỗng mãi mãi — {@link #has} trả {@code false}, {@code ComponentMapper.get} không thấy
 * gì, {@link #add} là no-op. Phải thế vì {@code PooledEngine} của Ashley cấp lại đúng entity gốc đó
 * cho lần {@code createEntity()} kế tiếp: handle cũ còn trỏ vào entity gốc thì đọc ra component của
 * một entity MỚI hoàn toàn khác, và ghi qua nó là ghi vào entity đó (hoặc, lúc entity gốc còn nằm
 * trong pool, cài sẵn component cho entity chưa ra đời).
 */
public final class KEntity {

    /** Entity gốc dùng chung của MỌI handle chết: không bao giờ vào engine, không bao giờ có component. */
    private static final com.badlogic.ashley.core.Entity DETACHED = new com.badlogic.ashley.core.Entity();

    private com.badlogic.ashley.core.Entity ashleyEntity;
    private final long id;

    KEntity(com.badlogic.ashley.core.Entity ashleyEntity, long id) {
        this.ashleyEntity = ashleyEntity;
        this.id = id;
    }

    /** Chỉ package này (ComponentMapper, EcsWorld, KSystem) được lấy Entity gốc của Ashley. */
    com.badlogic.ashley.core.Entity raw() {
        return ashleyEntity;
    }

    /** Chỉ {@link EcsWorld} gọi, đúng lúc entity gốc rời engine — từ đây handle này là handle chết. */
    void detach() {
        ashleyEntity = DETACHED;
    }

    public long id() {
        return id;
    }

    /** Gắn (hoặc ghi đè) component. No-op trên handle chết, cùng lệ với {@link EcsWorld#removeEntity}. */
    public <T extends Component> KEntity add(T component) {
        if (!ashleyEntity.equals(DETACHED)) {
            ashleyEntity.add(component);
        }
        return this;
    }

    public boolean has(Class<? extends Component> type) {
        return ashleyEntity.getComponent(type) != null;
    }
}
