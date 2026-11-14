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
 * Dọn định kỳ dữ liệu có thời hạn giữ. Hai luật:
 *
 * <ul>
 *   <li>{@code idempotency_key} cũ hơn {@link #IDEMPOTENCY_KEY_RETENTION} (quyết định chủ dự án 2026-10-11);
 *   <li>dòng {@code audit_log} {@code CHAT_MESSAGE} cũ hơn {@link #CHAT_AUDIT_RETENTION} (dòng 71(e), chủ
 *       dự án duyệt 2026-10-17). Từ ngày đó audit chat chở NỘI DUNG tin và người nhận tin riêng để xử lý
 *       báo cáo — dữ liệu riêng tư, nên chỉ giữ đủ lâu cho một báo cáo kịp tới. Không có chỉ mục theo
 *       {@code action}/{@code created_at} (thêm là đổi schema) ⇒ mỗi lượt quét cả bảng, 6 giờ một lần.
 * </ul>
 *
 * <p>Về {@code idempotency_key}:
 *
 * <p>Mỗi lệnh có hiệu ứng (mua, bán, cường hoá, lĩnh thư, lĩnh thưởng quest, xác nhận giao dịch, tạo
 * nhân vật) ghi một dòng vào bảng đó và trước đây không gì xoá nó: bảng chỉ tăng, mãi mãi. Khoá chỉ
 * có nghĩa trong cửa sổ client còn có thể gửi LẠI đúng lệnh đó — tính bằng giây tới phút — nên giữ
 * {@link #IDEMPOTENCY_KEY_RETENTION} là thừa rất xa.
 *
 * <p>Luồng nền riêng (daemon), không phải tick hay EventLoop. Lần dọn hỏng chỉ ghi WARN: lần sau dọn bù.
 */
final class RetentionJanitor {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionJanitor.class);

    /** Khoá cũ hơn ngần này bị xoá. */
    static final Duration IDEMPOTENCY_KEY_RETENTION = Duration.ofDays(7);

    /** Dòng audit chat (có nội dung tin) cũ hơn ngần này bị xoá. */
    static final Duration CHAT_AUDIT_RETENTION = Duration.ofDays(30);

    /** ADR-169 — sổ vàng cá nhân giữ 30 ngày. */
    static final Duration GOLD_LEDGER_RETENTION = Duration.ofDays(30);

    /** ADR-180 — nhật ký bang giữ 90 ngày. */
    static final Duration GUILD_LOG_RETENTION = Duration.ofDays(90);

    /** ADR-386 — thư đã đọc, không còn quà chưa nhận, không ghim, không lưu trữ: giữ 30 ngày kể từ lúc đọc. */
    static final Duration READ_MAIL_RETENTION = Duration.ofDays(30);

    private static final Duration FIRST_RUN_DELAY = Duration.ofMinutes(1);
    private static final Duration PERIOD = Duration.ofHours(6);

    private static final String DELETE_KEYS_OLDER_THAN = """
            DELETE FROM idempotency_key WHERE created_at < ?
            """;

    private static final String DELETE_GOLD_LEDGER_OLDER_THAN = "DELETE FROM gold_ledger_entry WHERE created_at < ?";

    private static final String DELETE_GUILD_LOG_OLDER_THAN = "DELETE FROM guild_log WHERE created_at < ?";

    /** ADR-187 — tin nhắn riêng chờ giao quá 7 ngày. */
    private static final String DELETE_OFFLINE_WHISPER_OLDER_THAN = "DELETE FROM offline_whisper WHERE created_at < ?";

    static final Duration OFFLINE_WHISPER_RETENTION = Duration.ofDays(7);

    private static final String DELETE_CHAT_AUDIT_OLDER_THAN = """
            DELETE FROM audit_log WHERE action = 'CHAT_MESSAGE' AND created_at < ?
            """;

    /**
     * ADR-386 — một câu lệnh: CTE chọn thư cũ, xoá {@code mail_item} của chúng rồi xoá thư (khoá ngoại kiểm ở cuối
     * câu lệnh). Thư có vàng/vật phẩm chưa nhận ({@code claimed_at IS NULL}) không bao giờ bị dọn.
     */
    private static final String DELETE_OLD_READ_MAIL = """
            WITH old AS (
                SELECT m.id FROM mail m
                 WHERE m.read_at < ? AND NOT m.pinned AND m.archived_at IS NULL
                   AND (m.claimed_at IS NOT NULL
                        OR (m.gold_amount = 0 AND NOT EXISTS (SELECT 1 FROM mail_item i WHERE i.mail_id = m.id)))
            ), items AS (
                DELETE FROM mail_item WHERE mail_id IN (SELECT id FROM old)
            )
            DELETE FROM mail WHERE id IN (SELECT id FROM old)
            """;

    private final DataSource dataSource;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "retention-janitor");
        thread.setDaemon(true);
        return thread;
    });

    RetentionJanitor(DataSource dataSource) {
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

    /** Xoá mọi khoá ghi trước {@code now - IDEMPOTENCY_KEY_RETENTION}. Package-private để test gọi thẳng. */
    int purgeIdempotencyKeys(Instant now) {
        return JdbcOperations.update(
                dataSource, DELETE_KEYS_OLDER_THAN, Timestamp.from(now.minus(IDEMPOTENCY_KEY_RETENTION)));
    }

    /** Xoá mọi dòng audit chat ghi trước {@code now - CHAT_AUDIT_RETENTION}. Package-private để test gọi thẳng. */
    int purgeGoldLedger(Instant now) {
        return JdbcOperations.update(
                dataSource, DELETE_GOLD_LEDGER_OLDER_THAN, Timestamp.from(now.minus(GOLD_LEDGER_RETENTION)));
    }

    /** ADR-180 — xoá dòng nhật ký bang cũ hơn {@link #GUILD_LOG_RETENTION}. Package-private để test gọi thẳng. */
    int purgeGuildLog(Instant now) {
        return JdbcOperations.update(
                dataSource, DELETE_GUILD_LOG_OLDER_THAN, Timestamp.from(now.minus(GUILD_LOG_RETENTION)));
    }

    /** ADR-187 — xoá tin nhắn riêng chờ giao quá {@link #OFFLINE_WHISPER_RETENTION}. */
    int purgeOfflineWhispers(Instant now) {
        return JdbcOperations.update(
                dataSource, DELETE_OFFLINE_WHISPER_OLDER_THAN, Timestamp.from(now.minus(OFFLINE_WHISPER_RETENTION)));
    }

    /** ADR-386 — dọn thư đã đọc cũ hơn {@link #READ_MAIL_RETENTION}. Package-private để test gọi thẳng. */
    int purgeOldReadMail(Instant now) {
        return JdbcOperations.update(dataSource, DELETE_OLD_READ_MAIL, Timestamp.from(now.minus(READ_MAIL_RETENTION)));
    }

    int purgeChatAudit(Instant now) {
        return JdbcOperations.update(
                dataSource, DELETE_CHAT_AUDIT_OLDER_THAN, Timestamp.from(now.minus(CHAT_AUDIT_RETENTION)));
    }

    // PMD AvoidCatchingGenericException: task định kỳ — một lượt hỏng (DB tạm gián đoạn) không được làm
    // ScheduledExecutorService âm thầm huỷ mọi lượt sau.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void purgeQuietly() {
        Instant now = Instant.now();
        try {
            int deleted = purgeIdempotencyKeys(now);
            if (deleted > 0) {
                LOG.info("Đã dọn {} khoá idempotency cũ hơn {} ngày", deleted, IDEMPOTENCY_KEY_RETENTION.toDays());
            }
        } catch (RuntimeException e) {
            LOG.warn("Dọn khoá idempotency thất bại — lượt sau dọn bù", e);
        }
        // Bắt RIÊNG: luật này hỏng không được kéo luật kia.
        try {
            int deleted = purgeChatAudit(now);
            if (deleted > 0) {
                LOG.info("Đã dọn {} dòng audit chat cũ hơn {} ngày", deleted, CHAT_AUDIT_RETENTION.toDays());
            }
        } catch (RuntimeException e) {
            LOG.warn("Dọn audit chat thất bại — lượt sau dọn bù", e);
        }
        try {
            int deleted = purgeGoldLedger(now);
            if (deleted > 0) {
                LOG.info("Đã dọn {} dòng sổ vàng cũ hơn {} ngày", deleted, GOLD_LEDGER_RETENTION.toDays());
            }
        } catch (RuntimeException e) {
            LOG.warn("Dọn sổ vàng thất bại — lượt sau dọn bù", e);
        }
        purgeSocialQuietly(now);
    }

    /** Nửa sau của {@link #purgeQuietly} (nhật ký bang, tin riêng chờ) — tách vì trần CyclomaticComplexity. */
    @SuppressWarnings("PMD.AvoidCatchingGenericException") // cùng lý do purgeQuietly
    private void purgeSocialQuietly(Instant now) {
        try {
            int deleted = purgeGuildLog(now);
            if (deleted > 0) {
                LOG.info("Đã dọn {} dòng nhật ký bang cũ hơn {} ngày", deleted, GUILD_LOG_RETENTION.toDays());
            }
        } catch (RuntimeException e) {
            LOG.warn("Dọn nhật ký bang thất bại — lượt sau dọn bù", e);
        }
        try {
            purgeOfflineWhispers(now);
        } catch (RuntimeException e) {
            LOG.warn("Dọn tin nhắn riêng chờ giao thất bại — lượt sau dọn bù", e);
        }
        try {
            int deleted = purgeOldReadMail(now);
            if (deleted > 0) {
                LOG.info("Đã dọn {} thư đã đọc cũ hơn {} ngày", deleted, READ_MAIL_RETENTION.toDays());
            }
        } catch (RuntimeException e) {
            LOG.warn("Dọn thư cũ thất bại — lượt sau dọn bù", e);
        }
    }
}
