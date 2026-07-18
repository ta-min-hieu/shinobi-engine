package dev.kage.engine.ecs;

import com.badlogic.ashley.core.PooledEngine;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thế giới ECS — bọc Ashley {@code PooledEngine} (ENGINE.md §5, ECS.md §10).
 *
 * <p>Duy trì một {@link KEntity} wrapper ổn định cho mỗi Ashley Entity (thay vì tạo mới mỗi lần
 * truy cập) để tránh allocation trong hot path (Điều 31) — xem {@link #wrap}. Tự cấp ID vì Ashley
 * hiện đại không expose ID số nguyên trên Entity (xem {@link KEntity}).
 */
public final class EcsWorld {

    private final PooledEngine ashleyEngine = new PooledEngine();
    private final Map<com.badlogic.ashley.core.Entity, KEntity> handles = new IdentityHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public KEntity createEntity() {
        var raw = ashleyEngine.createEntity();
        var handle = new KEntity(raw, nextId.getAndIncrement());
        handles.put(raw, handle);
        ashleyEngine.addEntity(raw);
        return handle;
    }

    public void removeEntity(KEntity entity) {
        var raw = entity.raw();
        ashleyEngine.removeEntity(raw);
        handles.remove(raw);
    }

    public void addSystem(IteratingKSystem system) {
        system.bind(this);
        ashleyEngine.addSystem(system.ashleySystem());
    }

    /** Fixed-timestep update — gọi từ KageEngine.fixedUpdate() với delta cố định (ENGINE.md §4). */
    public void update(float fixedDeltaTime) {
        ashleyEngine.update(fixedDeltaTime);
    }

    /**
     * Chỉ nội bộ package (IteratingKSystem adapter) tra wrapper theo Ashley entity. Không dùng
     * {@code computeIfAbsent} với {@code KEntity::new} vì constructor cần cấp {@code id} mới —
     * mọi entity PHẢI được tạo qua {@link #createEntity()}, wrap() chỉ tra cứu wrapper có sẵn.
     */
    KEntity wrap(com.badlogic.ashley.core.Entity raw) {
        var handle = handles.get(raw);
        if (handle == null) {
            throw new IllegalStateException(
                    "Entity không có KEntity handle — mọi entity phải tạo qua EcsWorld.createEntity()");
        }
        return handle;
    }
}
