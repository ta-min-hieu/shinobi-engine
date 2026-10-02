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

    // "varargs": chuyển tiếp mảng varargs sang Family.all của Ashley (không @SafeVarargs) — chỉ đọc,
    // không ghi gì vào mảng, nên không có heap pollution.
    @SafeVarargs
    @SuppressWarnings("varargs")
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
