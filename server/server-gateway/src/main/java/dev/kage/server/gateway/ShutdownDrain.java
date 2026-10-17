package dev.kage.server.gateway;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chờ một executor đã {@code shutdown()} chạy xong việc đang dở lúc tắt server (SERVER.md §10).
 *
 * <p>Sửa 2026-10-02: các registry chỉ {@code shutdown()} rồi trả về, và {@code GatewayBootstrap} đóng
 * pool DB ngay sau đó — HikariPool huỷ ngang connection đang dùng, nên lượt chốt trận Arena (Elo) hay
 * lượt đưa người gục về world chính đang dở bị cắt giữa chừng. Cùng lỗi đã sửa ở
 * {@code ConnectedCharacterRegistry#stop}.
 *
 * <p>⚠️ Gọi NGOÀI khoá của registry: việc đang dở thường cần chính khoá đó, chờ trong khoá thì nó kẹt
 * tới hết hạn.
 */
final class ShutdownDrain {

    /** Cùng trần với {@code ConnectedCharacterRegistry} — nằm gọn trong {@code stop_grace_period} 60s. */
    static final long TIMEOUT_SECONDS = 10L;

    private static final long MILLIS_PER_SECOND = 1_000L;

    private static final Logger LOG = LoggerFactory.getLogger(ShutdownDrain.class);

    private ShutdownDrain() {}

    static void await(ExecutorService executor, String what) {
        await(executor, what, TIMEOUT_SECONDS * MILLIS_PER_SECOND);
    }

    /**
     * Như {@link #await(ExecutorService, String)} với trần tuỳ chọn — cho test, không phải đợi 10 s.
     *
     * @return {@code true} nếu executor đã chạy xong trong trần
     */
    static boolean await(ExecutorService executor, String what, long timeoutMillis) {
        try {
            if (executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return true;
            }
            LOG.warn("{} chưa xong sau {}ms lúc tắt server — vẫn đóng tiếp", what, timeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }
}
