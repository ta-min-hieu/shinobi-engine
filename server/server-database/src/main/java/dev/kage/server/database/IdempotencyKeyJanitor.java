package dev.kage.server.database;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dọn định kỳ bảng {@code idempotency_key} (quyết định chủ dự án 2026-10-11).
 *
 * <p>Mỗi lệnh có hiệu ứng (mua, bán, cường hoá, lĩnh thư, lĩnh thưởng quest, xác nhận giao dịch, tạo
 * nhân vật) ghi một dòng vào bảng đó và trước đây không gì xoá nó: bảng chỉ tăng, mãi mãi. Khoá chỉ
 * có nghĩa trong cửa sổ client còn có thể gửi LẠI đúng lệnh đó — tính bằng giây tới phút — nên giữ
 * {@link #RETENTION} là thừa rất xa.
 *
 * <p>Luồng nền riêng (daemon), không phải tick hay EventLoop. Lần dọn hỏng chỉ ghi WARN: lần sau dọn bù.
 */
final class IdempotencyKeyJanitor {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyKeyJanitor.class);

    /** Khoá cũ hơn ngần này bị xoá. */
    static final Duration RETENTION = Duration.ofDays(7);

    private static final Duration FIRST_RUN_DELAY = Duration.ofMinutes(1);
    private static final Duration PERIOD = Duration.ofHours(6);

    private static final String DELETE_OLDER_THAN = """
            DELETE FROM idempotency_key WHERE created_at < ?
            """;

    private final DataSource dataSource;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "idempotency-key-janitor");
        thread.setDaemon(true);
        return thread;
    });

    IdempotencyKeyJanitor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // FutureReturnValueIgnored: lỗi từng lượt đã bị chặn ở purgeQuietly nên lịch không tự huỷ; Future
    // chỉ còn dùng để huỷ, và stop() tắt cả scheduler.
    @SuppressWarnings("FutureReturnValueIgnored")
    void start() {
        scheduler.scheduleAtFixedRate(
                this::purgeQuietly, FIRST_RUN_DELAY.toMillis(), PERIOD.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Dừng lịch — gọi TRƯỚC khi đóng pool DB. Lượt đang chạy dở (nếu có) bị ngắt; lần khởi động sau dọn bù. */
    void stop() {
        scheduler.shutdownNow();
    }

    /** Xoá mọi khoá ghi trước {@code now - RETENTION}. Package-private để test gọi thẳng, không chờ lịch. */
    int purge(Instant now) {
        return JdbcOperations.update(dataSource, DELETE_OLDER_THAN, Timestamp.from(now.minus(RETENTION)));
    }

    // PMD AvoidCatchingGenericException: task định kỳ — một lượt hỏng (DB tạm gián đoạn) không được làm
    // ScheduledExecutorService âm thầm huỷ mọi lượt sau.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void purgeQuietly() {
        try {
            int deleted = purge(Instant.now());
            if (deleted > 0) {
                LOG.info("Đã dọn {} khoá idempotency cũ hơn {} ngày", deleted, RETENTION.toDays());
            }
        } catch (RuntimeException e) {
            LOG.warn("Dọn khoá idempotency thất bại — lượt sau dọn bù", e);
        }
    }
}
