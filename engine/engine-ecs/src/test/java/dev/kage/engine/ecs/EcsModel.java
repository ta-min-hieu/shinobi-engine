package dev.kage.engine.ecs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.kage.engine.ecs.ProbeKinds.ProbeComponent;

/**
 * Mô hình ngây thơ của {@link EcsWorld}: danh sách entity sống, mỗi entity một mảng component theo
 * loại, và các handle đã chết. {@link #compareWith} đối chiếu TOÀN BỘ mô hình với world thật.
 */
final class EcsModel {

    /** Giữ bấy nhiêu handle chết gần nhất — đủ dài để entity gốc của chúng được cấp lại nhiều lần. */
    private static final int STALE_KEPT = 96;

    /** Một entity đang sống theo mô hình. */
    static final class ModelEntity {
        final KEntity handle;
        final ProbeComponent[] components = new ProbeComponent[ProbeKinds.COUNT];

        ModelEntity(KEntity handle) {
            this.handle = handle;
        }

        long id() {
            return handle.id();
        }

        boolean matches(int mask) {
            int own = 0;
            for (int kind = 0; kind < ProbeKinds.COUNT; kind++) {
                if (components[kind] != null) {
                    own |= 1 << kind;
                }
            }
            return (own & mask) == mask;
        }
    }

    private final List<ModelEntity> live = new ArrayList<>();
    private final Map<Long, ModelEntity> liveById = new HashMap<>();
    private final List<KEntity> stale = new ArrayList<>();

    List<ModelEntity> live() {
        return live;
    }

    List<KEntity> stale() {
        return stale;
    }

    Optional<ModelEntity> find(long id) {
        return Optional.ofNullable(liveById.get(id));
    }

    ModelEntity add(KEntity handle) {
        ModelEntity entity = new ModelEntity(handle);
        live.add(entity);
        liveById.put(handle.id(), entity);
        return entity;
    }

    void kill(ModelEntity entity) {
        live.remove(entity);
        liveById.remove(entity.id());
        stale.add(entity.handle);
        if (stale.size() > STALE_KEPT) {
            stale.remove(0);
        }
    }

    void killAll() {
        while (!live.isEmpty()) {
            kill(live.get(live.size() - 1));
        }
    }

    Set<Long> idsMatching(int mask) {
        Set<Long> ids = new HashSet<>();
        for (ModelEntity entity : live) {
            if (entity.matches(mask)) {
                ids.add(entity.id());
            }
        }
        return ids;
    }

    /** Ghi vào {@code violations} mọi chỗ world thật lệch khỏi mô hình. */
    void compareWith(EcsWorld world, List<String> violations) {
        if (world.entityCount() != live.size()) {
            violations.add("entityCount=" + world.entityCount() + " nhưng mô hình có " + live.size());
        }
        for (ModelEntity entity : live) {
            for (int kind = 0; kind < ProbeKinds.COUNT; kind++) {
                compareLive(entity, kind, violations);
            }
        }
        for (KEntity handle : stale) {
            for (int kind = 0; kind < ProbeKinds.COUNT; kind++) {
                if (ProbeKinds.mapperHas(kind, handle) || ProbeKinds.entityHas(kind, handle)) {
                    violations.add("handle chết #" + handle.id() + " vẫn thấy component loại " + kind);
                }
            }
        }
    }

    private static void compareLive(ModelEntity entity, int kind, List<String> violations) {
        ProbeComponent expected = entity.components[kind];
        boolean mapperHas = ProbeKinds.mapperHas(kind, entity.handle);
        boolean entityHas = ProbeKinds.entityHas(kind, entity.handle);
        if (mapperHas != (expected != null) || entityHas != (expected != null)) {
            violations.add("entity #" + entity.id() + " loại " + kind + ": mô hình có=" + (expected != null)
                    + " mapper.has=" + mapperHas + " entity.has=" + entityHas);
            return;
        }
        if (expected == null) {
            return;
        }
        if (!ProbeKinds.mapperReturns(kind, entity.handle, expected)) {
            violations.add("entity #" + entity.id() + " loại " + kind + ": mapper trả đối tượng khác mô hình");
        }
        if (expected.owner != entity.id()) {
            violations.add("entity #" + entity.id() + " mang component của #" + expected.owner);
        }
    }
}
