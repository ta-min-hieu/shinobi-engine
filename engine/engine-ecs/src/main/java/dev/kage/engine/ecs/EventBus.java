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
 * {@code publish()} không allocation — chỉ duyệt list listener đã có sẵn, theo chỉ số (đo
 * 2026-10-10, {@code EcsTickAllocationTest}: vòng for-each cũ cấp một {@code Iterator} 32 byte mỗi
 * lần phát mà C2 không khử được). Listener đăng ký GIỮA lúc một sự kiện đang phát không nhận sự kiện
 * đó — nó đã xảy ra trước khi listener tồn tại — và nhận từ sự kiện kế tiếp.
 */
public final class EventBus {

    private final Map<Class<?>, List<Consumer<?>>> listeners = new HashMap<>();

    public <T extends Event> void subscribe(Class<T> type, Consumer<T> listener) {
        listeners.computeIfAbsent(type, key -> new ArrayList<>()).add(listener);
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> void publish(T event) {
        List<Consumer<?>> list = listeners.get(event.getClass());
        if (list == null) {
            return;
        }
        // Chốt số listener TRƯỚC khi phát: listener nào subscribe thêm trong lúc xử lý thì nối vào
        // cuối list, ngoài khoảng đang duyệt (không có unsubscribe nên list chỉ dài ra).
        int count = list.size();
        for (int i = 0; i < count; i++) {
            ((Consumer<T>) list.get(i)).accept(event);
        }
    }
}
