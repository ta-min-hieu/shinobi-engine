package dev.kage.server.database;

/**
 * Port đo JDBC query/transaction (OBSERVABILITY.md §3.3 {@code kage.db.query.duration}/
 * {@code kage.db.transaction.failed}) — thực thi bởi {@code server-gateway} qua Micrometer
 * (ADR-015), cùng lý do {@code TickMetricsPort} (server-world) tồn tại: {@code server-database}
 * KHÔNG import {@code io.micrometer.*} trực tiếp (ArchUnit {@code
 * chi_server_gateway_dung_micrometer}).
 *
 * <p>Khác {@code TickMetricsPort} (inject qua constructor {@code WorldFacade}), port này cấu hình
 * 1 LẦN lúc khởi động qua {@link JdbcOperations#configureMetrics}/
 * {@link JdbcTransactionRunner#configureMetrics}: {@link JdbcOperations}/{@link
 * JdbcTransactionRunner} là static utility dùng bởi ~35 {@code Postgres*Adapter}
 * (131 call site {@code JdbcOperations} tính lúc viết lát cắt này) — thread {@link DbMetricsPort}
 * qua tham số từng call site là đổi chữ ký hàng loạt ngoài phạm vi lát cắt quan sát này. Field tĩnh
 * + {@link #NO_OP} mặc định giữ mọi test/call site hiện có không đổi hành vi.
 */
public interface DbMetricsPort {

    /** No-op mặc định — dùng cho test và mọi nơi chưa cấu hình registry thật. */
    DbMetricsPort NO_OP = new DbMetricsPort() {
        @Override
        public void recordQueryDuration(String operation, long durationNanos) {}

        @Override
        public void recordTransactionFailed() {}
    };

    /**
     * {@code operation} = SQL verb đầu câu (SELECT/INSERT/UPDATE/DELETE) lấy từ chính literal SQL
     * — KHÔNG phải nội dung SQL đầy đủ (OBSERVABILITY.md §4: tag phải hữu hạn và nhỏ).
     * {@code durationNanos} là thời gian JDBC prepare+execute vừa chạy xong mất bao lâu.
     */
    void recordQueryDuration(String operation, long durationNanos);

    /** Gọi khi 1 transaction JDBC rollback do action ném RuntimeException ({@code JdbcTransactionRunner}). */
    void recordTransactionFailed();
}
