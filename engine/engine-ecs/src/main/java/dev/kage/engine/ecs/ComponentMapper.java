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
        return new ComponentMapper<>(com.badlogic.ashley.core.ComponentMapper.getFor(type));
    }

    public T get(KEntity entity) {
        return delegate.get(entity.raw());
    }

    public boolean has(KEntity entity) {
        return delegate.has(entity.raw());
    }
}
