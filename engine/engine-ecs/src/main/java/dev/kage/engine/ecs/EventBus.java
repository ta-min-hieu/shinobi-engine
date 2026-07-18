package dev.kage.engine.ecs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Event Bus in-process, đồng bộ (ADR-008). System không gọi trực tiếp System khác — publish
 * Event, ai quan tâm thì tự subscribe (ECS.md §7).
 *
 * <p>{@code subscribe()} chỉ gọi lúc khởi tạo System (composition root), không trong tick.
 * {@code publish()} không allocation — chỉ duyệt list listener đã có sẵn.
 */
public final class EventBus {

    private final Map<Class<?>, List<Consumer<?>>> listeners = new HashMap<>();

    public <T extends Event> void subscribe(Class<T> type, Consumer<T> listener) {
        listeners.computeIfAbsent(type, key -> new ArrayList<>()).add(listener);
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> void publish(T event) {
        var list = listeners.get(event.getClass());
        if (list == null) {
            return;
        }
        for (var listener : list) {
            ((Consumer<T>) listener).accept(event);
        }
    }
}
