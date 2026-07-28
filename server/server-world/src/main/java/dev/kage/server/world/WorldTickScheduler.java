package dev.kage.server.world;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Pool thread CỐ ĐỊNH dùng chung để tick MỌI {@link WorldFacade} (channel map chính, dungeon, arena)
 * — thay cho mô hình cũ "1 OS thread riêng/World" (tốn ~N/20 thread khi số kênh tỉ lệ thẳng với tổng
 * người online, xem {@code MainWorldChannelRegistry}). Số thread bị chặn trần ở
 * {@link #POOL_SIZE}, không tăng theo số World đang chạy.
 *
 * <p>{@code scheduleAtFixedRate} của JDK tự đảm bảo 2 lần chạy CÙNG 1 task không bao giờ chồng lấn
 * (happens-before giữa các lần chạy kế tiếp) — đúng bất biến "1 World chỉ 1 thread chạm state tại 1
 * thời điểm" (SERVER.md §3) mà không cần khoá thủ công cho nhịp tick bình thường.
 *
 * <p>⚠️ {@code WorldFacade} PHẢI bọc {@code Throwable} quanh {@code Runnable} truyền vào đây —
 * {@code scheduleAtFixedRate} âm thầm NGỪNG lặp lại vĩnh viễn (không log) nếu Runnable ném exception
 * không bắt, tệ hơn hiện trạng cũ (thread chết vẫn có uncaught-exception-handler in ra stderr).
 */
final class WorldTickScheduler {

    private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final ScheduledExecutorService POOL =
            Executors.newScheduledThreadPool(POOL_SIZE, WorldTickScheduler::newDaemonThread);

    static ScheduledFuture<?> scheduleAtFixedRate(Runnable tickTask, long periodNanos) {
        return POOL.scheduleAtFixedRate(tickTask, 0, periodNanos, TimeUnit.NANOSECONDS);
    }

    private static Thread newDaemonThread(Runnable r) {
        Thread thread = new Thread(r, "world-tick-pool");
        thread.setDaemon(true);
        return thread;
    }

    private WorldTickScheduler() {
    }
}
