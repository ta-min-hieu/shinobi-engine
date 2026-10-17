package dev.kage.server.gateway;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Chạy các việc lần lượt, ĐÚNG thứ tự nhận, trên một {@link Executor} bất kỳ — mỗi lúc nhiều nhất một
 * việc, không giữ luồng nào khi hàng rỗng.
 *
 * <p>Sinh ra cho chat (dòng 71(f), chủ dự án duyệt 2026-10-17): mỗi tin chat là một Virtual Thread
 * riêng của {@code authExecutor} (tra tên người nhận, ghi audit — I/O chặn), nên hai tin gửi sát nhau
 * có thể đua nhau và tới người nhận theo thứ tự ngược. Mỗi kết nối giữ một {@code SerialExecutor} cho
 * chat: tin sau chỉ bắt đầu khi tin trước đã phát xong.
 *
 * <p>Một việc ném ra không làm kẹt hàng: phần còn lại được chuyển sang một lượt chạy mới trước khi
 * ngoại lệ đi tiếp lên {@code delegate}.
 */
final class SerialExecutor implements Executor {

    private final Executor delegate;
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private boolean draining;

    SerialExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable task) {
        synchronized (this) {
            tasks.add(task);
            if (draining) {
                return;
            }
            draining = true;
        }
        startDraining();
    }

    private void startDraining() {
        try {
            delegate.execute(this::drain);
        } catch (RejectedExecutionException e) {
            // Executor đã đóng (server đang tắt): không ai chạy hàng này nữa — bỏ, để lần gọi sau thấy
            // đúng lỗi của executor thay vì kẹt im lặng sau cờ draining.
            synchronized (this) {
                tasks.clear();
                draining = false;
            }
            throw e;
        }
    }

    private void drain() {
        Runnable next = poll();
        while (next != null) {
            boolean completed = false;
            try {
                next.run();
                completed = true;
            } finally {
                if (!completed) {
                    continueAfterFailure();
                }
            }
            next = poll();
        }
    }

    /** Việc vừa ném — giao phần còn lại cho một lượt chạy mới, lượt hiện tại kết thúc cùng ngoại lệ. */
    private void continueAfterFailure() {
        synchronized (this) {
            if (tasks.isEmpty()) {
                draining = false;
                return;
            }
        }
        startDraining();
    }

    /** Việc kế tiếp, hoặc {@code null} khi hàng rỗng — lúc đó cờ {@code draining} đã được hạ. */
    private synchronized Runnable poll() {
        Runnable next = tasks.poll();
        if (next == null) {
            draining = false;
        }
        return next;
    }
}
