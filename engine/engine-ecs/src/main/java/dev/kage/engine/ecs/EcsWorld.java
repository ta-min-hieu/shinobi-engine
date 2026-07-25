package dev.kage.engine.ecs;

import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thế giới ECS — bọc Ashley {@code PooledEngine} (ENGINE.md §5, ECS.md §10).
 *
 * <p>Duy trì một {@link KEntity} wrapper ổn định cho mỗi Ashley Entity (thay vì tạo mới mỗi lần
 * truy cập) để tránh allocation trong hot path (Điều 31) — xem {@link #wrap}. Tự cấp ID vì Ashley
 * hiện đại không expose ID số nguyên trên Entity (xem {@link KEntity}).
 *
 * <p>⚠️ PHÁT HIỆN THẬT (Phase 4): KHÔNG đăng ký {@link IteratingKSystem} qua Ashley
 * {@code Engine.addSystem}/chạy qua {@code Engine.update} — xem javadoc {@link IteratingKSystem}
 * vì sao (Ashley coi 2 system dùng chung 1 lớp adapter là "cùng 1 system", system thứ 2 âm thầm
 * gỡ system thứ nhất). Thay vào đó {@link #update} tự lặp qua
 * {@code ashleyEngine.getEntitiesFor(family)} cho MỖI system đã đăng ký — không phụ thuộc
 * {@code getClass()}, an toàn với bao nhiêu system cũng được.
 */
public final class EcsWorld {

    private final PooledEngine ashleyEngine = new PooledEngine();
    private final Map<com.badlogic.ashley.core.Entity, KEntity> handles = new IdentityHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final List<IteratingKSystem> systems = new ArrayList<>();

    public KEntity createEntity() {
        com.badlogic.ashley.core.Entity raw = ashleyEngine.createEntity();
        KEntity handle = new KEntity(raw, nextId.getAndIncrement());
        handles.put(raw, handle);
        ashleyEngine.addEntity(raw);
        return handle;
    }

    public void removeEntity(KEntity entity) {
        com.badlogic.ashley.core.Entity raw = entity.raw();
        ashleyEngine.removeEntity(raw);
        handles.remove(raw);
    }

    public void addSystem(IteratingKSystem system) {
        systems.add(system);
    }

    /** Fixed-timestep update — gọi từ KageEngine.fixedUpdate() với delta cố định (ENGINE.md §4). */
    public void update(float fixedDeltaTime) {
        for (IteratingKSystem system : systems) {
            ImmutableArray<com.badlogic.ashley.core.Entity> entities =
                    ashleyEngine.getEntitiesFor(system.family().raw());
            for (int i = 0; i < entities.size(); i++) {
                system.processEntity(wrap(entities.get(i)), fixedDeltaTime);
            }
        }
    }

    /**
     * Chỉ nội bộ package (IteratingKSystem adapter) tra wrapper theo Ashley entity. Không dùng
     * {@code computeIfAbsent} với {@code KEntity::new} vì constructor cần cấp {@code id} mới —
     * mọi entity PHẢI được tạo qua {@link #createEntity()}, wrap() chỉ tra cứu wrapper có sẵn.
     */
    KEntity wrap(com.badlogic.ashley.core.Entity raw) {
        KEntity handle = handles.get(raw);
        if (handle == null) {
            throw new IllegalStateException(
                    "Entity không có KEntity handle — mọi entity phải tạo qua EcsWorld.createEntity()");
        }
        return handle;
    }
}
