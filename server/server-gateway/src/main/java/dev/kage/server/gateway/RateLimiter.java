package dev.kage.server.gateway;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Rate limit dạng fixed-window, KHÔNG dùng Redis — dự án single-instance (ADR-010 single-realm,
 * chưa scale ngang), đúng nhánh "in-memory nếu single instance" mà NETWORK.md §13 cho phép, đơn
 * giản hơn Redis mà không mất gì (Điều 9).
 *
 * <p>Per-connection: {@code GatewayChannelHandler} đã là state per-connection (mỗi kết nối 1
 * instance handler, xem javadoc lớp đó), nên field kiểu này tự nhiên đã cô lập theo connection.
 *
 * <p>2 tầng phản ứng theo đúng NETWORK.md §13: vượt ngưỡng 1 window → {@link #tryAcquire()} trả
 * {@code false} (caller drop packet + log); vượt NHIỀU window LIÊN TIẾP →
 * {@link #shouldDisconnect()} trả {@code true} (caller ngắt kết nối).
 */
final class RateLimiter {

    private static final int SUSTAINED_VIOLATION_WINDOWS = 3;

    private final int maxPerWindow;
    private final long windowNanos;
    private final LongSupplier nanoTimeSource;

    private long windowStartNanos;
    private int countInWindow;
    private boolean violatedThisWindow;
    private int consecutiveViolatedWindows;

    RateLimiter(int maxPerWindow, Duration window) {
        this(maxPerWindow, window, System::nanoTime);
    }

    /** Constructor cho test — {@code nanoTimeSource} giả để không phụ thuộc thời gian thật trôi. */
    RateLimiter(int maxPerWindow, Duration window, LongSupplier nanoTimeSource) {
        this.maxPerWindow = maxPerWindow;
        this.windowNanos = window.toNanos();
        this.nanoTimeSource = nanoTimeSource;
        this.windowStartNanos = nanoTimeSource.getAsLong();
    }

    /**
     * Đếm một request vào cửa sổ hiện tại.
     *
     * @return {@code true} nếu request được phép; {@code false} nếu vượt ngưỡng — caller PHẢI drop.
     */
    boolean tryAcquire() {
        rotateWindowIfElapsed();
        countInWindow++;
        if (countInWindow > maxPerWindow) {
            violatedThisWindow = true;
            return false;
        }
        return true;
    }

    /** {@code true} nếu vượt ngưỡng LIÊN TIẾP đủ lâu để coi là lạm dụng thật, không phải 1 burst ngắn. */
    boolean shouldDisconnect() {
        return consecutiveViolatedWindows >= SUSTAINED_VIOLATION_WINDOWS;
    }

    private void rotateWindowIfElapsed() {
        long now = nanoTimeSource.getAsLong();
        long elapsed = now - windowStartNanos;
        if (elapsed < windowNanos) {
            return;
        }
        // >= 2 window trôi qua nghĩa là có ít nhất MỘT window hoàn toàn im lặng ở giữa (không
        // tryAcquire() nào rơi vào đó, vì rotate chỉ chạy khi có lệnh gọi tới) — window im lặng đó
        // chắc chắn KHÔNG vi phạm (không gọi thì không đếm), nên chuỗi liên tục phải đứt ở đây.
        // Không kiểm điều này thì cờ violatedThisWindow của window active gần nhất — có thể đã cách
        // đây rất lâu — bị tính nhầm thành "window liền kề vừa vi phạm", cộng dồn sai vào
        // consecutiveViolatedWindows dù hai window đó không hề liên tục về thời gian thật.
        boolean immediatelyAdjacentWindow = elapsed < 2 * windowNanos;
        consecutiveViolatedWindows =
                immediatelyAdjacentWindow && violatedThisWindow ? consecutiveViolatedWindows + 1 : 0;
        windowStartNanos = now;
        countInWindow = 0;
        violatedThisWindow = false;
    }
}
