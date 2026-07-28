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
        public void recordTickDuration(long durationNanos) {
        }

        @Override
        public void recordTickOverrun() {
        }
    };

    /** {@code durationNanos} là thời gian {@link WorldFacade#tickOnce} vừa chạy xong mất bao lâu. */
    void recordTickDuration(long durationNanos);

    /**
     * Gọi khi 1 tick vượt ngân sách {@link WorldConstants#FIXED_DT} (OBSERVABILITY.md §3.1
     * {@code kage.tick.overrun.count}) — cùng điều kiện với log WARN "World tick quá hạn" ở
     * {@link WorldFacade#tickLoop}, nhưng là Counter đếm được/dashboard được thay vì chỉ đọc log.
     */
    void recordTickOverrun();
}
