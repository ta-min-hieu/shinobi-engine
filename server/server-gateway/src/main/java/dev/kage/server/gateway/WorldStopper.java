package dev.kage.server.gateway;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.server.world.WorldFacade;

/**
 * Dừng một {@link WorldFacade} KHÔNG chặn luồng gọi (2026-10-02).
 *
 * <p>{@link WorldFacade#stop()} chờ lần tick đang chạy dở xong hẳn — thường dưới 1ms, nhưng có thể
 * cả một tick, và script skill kéo một tick dài vài ms. Các registry gọi nó trên Netty EventLoop
 * (rớt mạng) và/hoặc trong khoá mà EventLoop xin cho MỌI gói di chuyển — cả hai thứ server/CLAUDE.md
 * cấm ("EventLoop: chờ lock"). Chỉ dùng ở chỗ KHÔNG có thứ tự nào phụ thuộc vào việc World đã dừng
 * xong khi lời gọi trả về: người chờ "World cũ đã im" phải chờ {@link WorldFacade#inboxBarrier()} —
 * mốc đó nay chờ cả một {@code stop()} đang dở.
 */
final class WorldStopper {

    private static final Logger LOG = LoggerFactory.getLogger(WorldStopper.class);

    private WorldStopper() {}

    /**
     * Xếp {@code stop()} lên {@code executor}. Executor đã tắt (server đang tắt — {@code stopAll} của
     * registry chạy rồi) thì dừng ngay trên luồng này: lúc đó không còn gói nào cần EventLoop phục vụ.
     */
    static void stopInBackground(ExecutorService executor, WorldFacade world) {
        try {
            executor.execute(world::stop);
        } catch (RejectedExecutionException e) {
            LOG.debug("Executor đã tắt, dừng World ngay trên luồng gọi", e);
            world.stop();
        }
    }
}
