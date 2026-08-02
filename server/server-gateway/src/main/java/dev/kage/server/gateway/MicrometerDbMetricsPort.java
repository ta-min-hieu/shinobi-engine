package dev.kage.server.gateway;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import dev.kage.server.database.DbMetricsPort;

/**
 * Implementation thật của {@link DbMetricsPort} (ADR-015, OBSERVABILITY.md §3.3) —
 * {@code kage.db.query.duration} (Timer, tag {@code operation} = verb đầu câu SQL —
 * SELECT/INSERT/UPDATE/DELETE, KHÔNG phải nội dung SQL đầy đủ: cardinality của literal SQL trong
 * ~35 Adapter tuy hữu hạn nhưng không nhỏ, "operation" giữ tag ở mức an toàn theo §4) +
 * {@code kage.db.transaction.failed} (Counter, không tag — 1 con số toàn server đủ dùng, xem
 * {@code JdbcTransactionRunner#rollbackQuietly}).
 *
 * <p>Khác {@link MicrometerTickMetricsPort} (pre-build hết Timer lúc construct vì biết trước tập
 * tên System cố định), Timer cho verb SQL được build LẤY qua {@code computeIfAbsent} — tập verb
 * không biết trước cứng như tên System, nhưng vẫn an toàn: verb đến từ literal SQL viết tay trong
 * code (server/CLAUDE.md "PreparedStatement, không nối chuỗi SQL"), không phải input người chơi,
 * nên không có nguy cơ cardinality nổ như tag theo characterId (OBSERVABILITY.md §4).
 */
final class MicrometerDbMetricsPort implements DbMetricsPort {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Timer> queryTimers = new ConcurrentHashMap<>();
    private final Counter transactionFailedCounter;

    MicrometerDbMetricsPort(MeterRegistry registry) {
        this.registry = registry;
        this.transactionFailedCounter = Counter.builder("kage.db.transaction.failed")
                .description("Số lần 1 transaction JDBC rollback do exception (JdbcTransactionRunner)")
                .register(registry);
    }

    @Override
    public void recordQueryDuration(String operation, long durationNanos) {
        queryTimers.computeIfAbsent(operation, this::buildQueryTimer).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private Timer buildQueryTimer(String operation) {
        return Timer.builder("kage.db.query.duration")
                .description("Thời gian 1 câu JDBC (query/update/insert/lock) chạy xong (JdbcOperations)")
                .tag("operation", operation)
                .register(registry);
    }

    @Override
    public void recordTransactionFailed() {
        transactionFailedCounter.increment();
    }
}
