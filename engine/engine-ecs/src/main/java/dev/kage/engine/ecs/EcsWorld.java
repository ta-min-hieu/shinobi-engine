package dev.kage.engine.ecs;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;

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
    private final IdentityHashMap<com.badlogic.ashley.core.Entity, KEntity> handles = new IdentityHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final List<IteratingKSystem> systems = new ArrayList<>();

    /**
     * Entity bị gỡ trong lúc {@link #update} đang lặp — gỡ thật sau khi System hiện tại lặp xong.
     * Cấp sẵn một lần, không cấp phát mỗi tick (Điều 31).
     */
    private final List<KEntity> pendingRemovals = new ArrayList<>();

    /**
     * System đang lặp trong {@link #update}, {@code null} khi không lặp. Là tham chiếu chứ không phải
     * cờ {@code boolean} chỉ vì SpotBugs coi lớp có {@code AtomicLong} là lớp dùng chung giữa nhiều
     * luồng và chặn mọi field primitive ghi không đồng bộ — EcsWorld chỉ chạy trên một luồng tick.
     */
    private IteratingKSystem iteratingSystem;

    public KEntity createEntity() {
        com.badlogic.ashley.core.Entity raw = ashleyEngine.createEntity();
        KEntity handle = new KEntity(raw, nextId.getAndIncrement());
        handles.put(raw, handle);
        ashleyEngine.addEntity(raw);
        return handle;
    }

    /**
     * Gỡ entity khỏi world. Gọi lại với cùng handle là no-op an toàn.
     *
     * <p>Gọi giữa lúc {@link #update} đang lặp thì entity được gỡ ngay sau khi System HIỆN TẠI lặp
     * xong (cùng ngữ nghĩa Ashley {@code Engine.update}): System đó vẫn xử lý đủ mọi entity còn lại,
     * các System sau không còn thấy entity này. Gỡ ngay tại chỗ thì sai: mảng Family của Ashley
     * không giữ thứ tự, gỡ một phần tử là kéo phần tử CUỐI vào chỗ trống, nên entity cuối mất lượt.
     *
     * <p>Handle cũ (đã gỡ, hoặc có từ trước {@link #clear}) là no-op: {@code PooledEngine} cấp lại
     * đúng entity gốc đó cho lần {@link #createEntity} kế tiếp, nên gỡ theo handle cũ từng gỡ mất
     * một entity MỚI hoàn toàn khác.
     */
    public void removeEntity(KEntity entity) {
        if (!entity.equals(handles.get(entity.raw()))) {
            return; // handle đã hết hạn — entity gốc có thể đang thuộc về entity khác.
        }
        if (iteratingSystem != null) {
            if (!pendingRemovals.contains(entity)) {
                pendingRemovals.add(entity);
            }
            return;
        }
        removeNow(entity);
    }

    private void removeNow(KEntity entity) {
        com.badlogic.ashley.core.Entity raw = entity.raw();
        ashleyEngine.removeEntity(raw);
        handles.remove(raw);
    }

    private void flushPendingRemovals() {
        // Index loop (không for-each) — tránh Iterator allocation trong game loop (Điều 31).
        for (int i = 0; i < pendingRemovals.size(); i++) {
            removeNow(pendingRemovals.get(i));
        }
        pendingRemovals.clear();
    }

    public void addSystem(IteratingKSystem system) {
        systems.add(system);
    }

    /**
     * Quên MỌI entity và MỌI System — đưa world về trạng thái vừa tạo.
     *
     * <p>Dành cho lúc rời hẳn một phiên chơi mà {@link EcsWorld} vẫn sống tiếp (client: engine giữ
     * một world duy nhất suốt đời ứng dụng, còn màn world thì đăng ký System của nó mỗi lần dựng —
     * bị đá về màn đăng nhập rồi vào lại mà không dọn thì System di chuyển chạy HAI lần mỗi tick).
     * Không gọi giữa lúc {@link #update} đang lặp.
     */
    public void clear() {
        ashleyEngine.removeAllEntities();
        handles.clear();
        pendingRemovals.clear();
        systems.clear();
    }

    /** Tổng số entity đang sống — OBSERVABILITY.md §3.1 {@code kage.entities.count}. */
    public int entityCount() {
        return ashleyEngine.getEntities().size();
    }

    /**
     * Đo thời gian mỗi System xử lý xong toàn bộ entity của nó trong 1 lần {@link #update} —
     * OBSERVABILITY.md §3.1 {@code kage.ecs.system.duration}. Định nghĩa ở {@code engine-ecs} (không
     * phải {@code server-world}) vì đây là nơi DUY NHẤT biết danh sách System thật đang chạy;
     * {@code server-world} chỉ implement listener này qua {@code TickMetricsPort} (Điều 23 — không
     * import Micrometer trực tiếp ở đây).
     */
    public interface SystemTimingListener {
        SystemTimingListener NO_OP = (systemName, durationNanos) -> {};

        void onSystemUpdated(String systemName, long durationNanos);
    }

    /** Fixed-timestep update — gọi từ KageEngine.fixedUpdate() với delta cố định (ENGINE.md §4). */
    public void update(float fixedDeltaTime) {
        update(fixedDeltaTime, SystemTimingListener.NO_OP);
    }

    /** Như {@link #update(float)} nhưng báo thời gian từng System qua {@code listener} mỗi lần gọi. */
    public void update(float fixedDeltaTime, SystemTimingListener listener) {
        for (IteratingKSystem system : systems) {
            long start = System.nanoTime();
            ImmutableArray<com.badlogic.ashley.core.Entity> entities =
                    ashleyEngine.getEntitiesFor(system.family().raw());
            iteratingSystem = system;
            try {
                for (int i = 0; i < entities.size(); i++) {
                    system.processEntity(wrap(entities.get(i)), fixedDeltaTime);
                }
            } finally {
                // finally: một System ném lỗi (server bắt và tick tiếp — ADR-026) không được bỏ lại
                // world kẹt ở chế độ "đang lặp", nơi mọi removeEntity về sau chỉ xếp hàng mãi.
                iteratingSystem = null;
                flushPendingRemovals();
            }
            listener.onSystemUpdated(system.getClass().getSimpleName(), System.nanoTime() - start);
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
