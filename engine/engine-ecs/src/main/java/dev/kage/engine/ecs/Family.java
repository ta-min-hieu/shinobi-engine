package dev.kage.engine.ecs;

/**
 * Bọc {@code com.badlogic.ashley.core.Family} — bộ lọc Entity theo Component (ECS.md §5).
 *
 * <p>{@code Family.all(Position.class, Velocity.class).get()}
 */
public final class Family {

    private final com.badlogic.ashley.core.Family ashleyFamily;

    private Family(com.badlogic.ashley.core.Family ashleyFamily) {
        this.ashleyFamily = ashleyFamily;
    }

    @SafeVarargs
    public static Builder all(Class<? extends Component>... types) {
        return new Builder(com.badlogic.ashley.core.Family.all(types));
    }

    /** Chỉ package này (KSystem) cần Family gốc để tạo Ashley IteratingSystem. */
    com.badlogic.ashley.core.Family raw() {
        return ashleyFamily;
    }

    public static final class Builder {
        private final com.badlogic.ashley.core.Family.Builder delegate;

        private Builder(com.badlogic.ashley.core.Family.Builder delegate) {
            this.delegate = delegate;
        }

        public Family get() {
            return new Family(delegate.get());
        }
    }
}
