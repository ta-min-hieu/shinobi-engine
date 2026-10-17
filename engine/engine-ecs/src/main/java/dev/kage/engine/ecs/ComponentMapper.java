package dev.kage.engine.ecs;

/**
 * Bọc {@code com.badlogic.ashley.core.ComponentMapper} — tra Component nhanh, không dùng
 * {@code getComponent()} trong vòng lặp nóng (ECS.md §5).
 *
 * <p>Dùng: {@code private static final ComponentMapper<Position> MAP = ComponentMapper.getFor(Position.class);}
 */
public final class ComponentMapper<T extends Component> {

    private final com.badlogic.ashley.core.ComponentMapper<T> delegate;

    private ComponentMapper(com.badlogic.ashley.core.ComponentMapper<T> delegate) {
        this.delegate = delegate;
    }

    public static <T extends Component> ComponentMapper<T> getFor(Class<T> type) {
        // Ashley đăng ký kiểu vào sổ ComponentType tĩnh ở đây — đi qua cùng khoá với ComponentTypes/Family.
        return new ComponentMapper<>(AshleyStatics.mapper(type));
    }

    /**
     * Component của {@code entity}, {@code null} nếu entity sống mà không có component này.
     *
     * @throws IllegalStateException nếu {@code entity} là handle chết ({@link KEntity#isAlive}) — đọc
     *     qua tham chiếu treo luôn là lỗi của bên gọi, không phải "không có component"
     */
    public T get(KEntity entity) {
        entity.requireAlive();
        return delegate.get(entity.raw());
    }

    public boolean has(KEntity entity) {
        return delegate.has(entity.raw());
    }
}
