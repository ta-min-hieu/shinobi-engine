package dev.kage.server.world;

/**
 * Port đo thời lượng 1 tick — thực thi bởi {@code server-gateway} qua Micrometer (ADR-015).
 * server-world KHÔNG import {@code io.micrometer.*} trực tiếp (server/CLAUDE.md — gameplay chỉ
 * phụ thuộc abstraction), cùng lý do {@link WorldOutboundSink} tồn tại cho output thay vì gọi
 * thẳng Netty.
 *
 * <p>Gọi trên world tick thread (SERVER.md §3) — implementation phía gateway chỉ được ghi vào
 * {@code Timer} đã tạo sẵn (không allocation, không blocking I/O), KHÔNG được block tick thread.
 */
public interface TickMetricsPort {

    /** No-op mặc định — dùng cho test và World không cần đo (ArenaInstanceRegistry, test đơn vị). */
    TickMetricsPort NO_OP = new TickMetricsPort() {
        @Override
        public void recordTickDuration(long durationNanos) {}

        @Override
        public void recordTickOverrun() {}

        @Override
        public void recordSystemDuration(String systemName, long durationNanos) {}

        @Override
        public void recordEntityCount(int count) {}
    };

    /** {@code durationNanos} là thời gian {@link WorldFacade#tickOnce} vừa chạy xong mất bao lâu. */
    void recordTickDuration(long durationNanos);

    /**
     * Gọi khi 1 tick vượt ngân sách {@link WorldConstants#FIXED_DT} (OBSERVABILITY.md §3.1
     * {@code kage.tick.overrun.count}) — cùng điều kiện với log WARN "World tick quá hạn" ở
     * {@code WorldFacade.tickTask}, nhưng là Counter đếm được/dashboard được thay vì chỉ đọc log.
     */
    void recordTickOverrun();

    /**
     * {@code systemName} = {@code getClass().getSimpleName()} của 1 {@code IteratingKSystem}
     * (OBSERVABILITY.md §3.1 {@code kage.ecs.system.duration}) — tập tên CỐ ĐỊNH, biết trước lúc
     * construct (xem {@code WorldFacade} constructor thêm System nào), nên implementation phía
     * gateway được phép pre-build Timer cho từng tên, không tạo mới trên tick thread (Điều 31).
     */
    void recordSystemDuration(String systemName, long durationNanos);

    /** OBSERVABILITY.md §3.1 {@code kage.entities.count} — tổng entity ECS đang sống lúc tick vừa xong. */
    void recordEntityCount(int count);
}
