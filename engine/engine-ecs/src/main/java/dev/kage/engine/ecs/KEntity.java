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
 * <p>Handle của entity đã gỡ (hoặc có từ trước {@link EcsWorld#clear}) là handle CHẾT
 * ({@link #isAlive} {@code false}): {@link #has} và {@code ComponentMapper.has} trả {@code false}, còn
 * {@link #add} và {@code ComponentMapper.get} NÉM {@link IllegalStateException} (fail-fast từ 2026-10-14;
 * trước đó là no-op im lặng). Handle phải tách khỏi entity gốc vì {@code PooledEngine} của Ashley
 * cấp lại đúng entity gốc đó cho lần {@code createEntity()} kế tiếp: handle cũ còn trỏ vào entity
 * gốc thì đọc ra component của một entity MỚI hoàn toàn khác, và ghi qua nó là ghi vào entity đó
 * (hoặc, lúc entity gốc còn nằm trong pool, cài sẵn component cho entity chưa ra đời).
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

    /**
     * {@code false} khi entity đã bị gỡ (hoặc có từ trước {@link EcsWorld#clear}) — handle chết. Entity
     * đang chờ gỡ cuối System hiện tại (gỡ giữa lúc {@link EcsWorld#update}) vẫn còn sống.
     */
    public boolean isAlive() {
        return !ashleyEntity.equals(DETACHED);
    }

    /**
     * Gắn (hoặc ghi đè) component.
     *
     * @throws IllegalStateException trên handle chết — ghi vào entity đã gỡ luôn là lỗi của bên gọi
     *     (quyết định chủ dự án 2026-10-14, README dòng 46(i)); trước đó là no-op im lặng, nên một
     *     tham chiếu treo làm mất dữ liệu mà không ai biết
     */
    public <T extends Component> KEntity add(T component) {
        requireAlive();
        ashleyEntity.add(component);
        return this;
    }

    /** Ném nếu handle đã chết — dùng ở mọi đường GHI/ĐỌC component; {@link #has} vẫn là câu hỏi hợp lệ. */
    void requireAlive() {
        if (!isAlive()) {
            throw new IllegalStateException("Entity " + id + " đã bị gỡ khỏi world — handle chết (ECS.md §2)");
        }
    }

    public boolean has(Class<? extends Component> type) {
        return ashleyEntity.getComponent(type) != null;
    }
}
