package dev.kage.server.gateway;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chạy các việc trao thưởng sau một cú hạ quái (rơi đồ, tiến độ quest, EXP) NGOÀI world tick thread.
 *
 * <p>{@code WorldOutboundSink#onMonsterKilled} bị gọi TRÊN tick thread, mà cả ba việc đều chạm DB
 * (server/CLAUDE.md: Game Tick cấm blocking I/O) — mỗi việc lên một Virtual Thread riêng, cùng pattern
 * {@code authExecutor} của {@code GatewayChannelHandler}. Riêng từng việc, để việc này chạm DB chậm
 * hay hỏng không kéo theo việc kia.
 *
 * <p><b>Vì sao tách khỏi {@link ConnectedCharacterRegistry} (2026-10-10).</b> Bản cũ {@code execute}
 * thẳng lên executor, không bọc gì:
 *
 * <ul>
 *   <li>Một việc ném (DB chập chờn, hết connection trong pool) thì exception chết trong Virtual Thread
 *       — chỉ in {@code Exception in thread ""} ra stderr, không qua logger, không nói mất phần thưởng
 *       gì của ai. Người chơi hạ quái mà không được gì, và không có dòng log nào để lần ra.
 *   <li>Lời báo tới SAU {@link #stop()} làm {@code execute} ném {@link RejectedExecutionException}
 *       ngay trên tick thread: {@code WorldFacade.tickOnce} bắt được nhưng bỏ phần còn lại của tick đó.
 * </ul>
 *
 * <p>⚠️ KHÔNG thử lại: phần thưởng của một việc hỏng là MẤT (quái không được lưu, không có gì để chạy
 * lại) — có thử lại hay không, có hàng đợi bền hay không là quyết định của chủ dự án. Lớp này chỉ bảo
 * đảm mất thì có dấu vết.
 */
final class KillRewardDispatcher {

    /** Việc nào trong ba việc — nhãn {@code step=} trong log, để lọc được theo loại phần thưởng mất. */
    enum Step {
        LOOT,
        QUEST_PROGRESS,
        EXPERIENCE,
        /** Tra xem đồng đội nào được chia phần — hỏng thì KHÔNG đồng đội nào được gì, người hạ vẫn đủ. */
        PARTY_SHARE,
        /** Bộ đếm thành tựu "hạ N quái" (ADR-062) — việc phụ. */
        ACHIEVEMENT
    }

    private static final Logger LOG = LoggerFactory.getLogger(KillRewardDispatcher.class);
    private static final long MILLIS_PER_SECOND = 1_000L;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** Lượt trao thưởng đã xếp mà chưa xong — để lúc tắt quá trần nói được ai còn dở (dòng 78(b)). */
    private final Set<Pending> pending = ConcurrentHashMap.newKeySet();

    /** Một lượt đang dở. Đối tượng riêng mỗi lượt: hai lượt cùng nội dung vẫn là hai lượt. */
    private static final class Pending {
        private final Step step;
        private final long characterId;
        private final String monsterCode;

        Pending(Step step, long characterId, String monsterCode) {
            this.step = step;
            this.characterId = characterId;
            this.monsterCode = monsterCode;
        }

        @Override
        public String toString() {
            return "step=" + step + " characterId=" + characterId + " monsterCode=" + monsterCode;
        }
    }

    /**
     * Xếp một việc trao thưởng. Không bao giờ ném, không bao giờ chạy {@code work} trên luồng gọi.
     *
     * <p>Đã {@link #stop()} thì BỎ việc đó kèm một dòng WARN, không chạy tại chỗ như
     * {@code WorldStopper}/{@code CharacterPositionPersister}: luồng gọi ở đây là tick thread, và pool
     * DB đóng ngay sau {@code stop()} trong trình tự tắt.
     */
    void dispatch(Step step, long killerCharacterId, String monsterCode, Runnable work) {
        Pending task = new Pending(step, killerCharacterId, monsterCode);
        pending.add(task);
        try {
            executor.execute(() -> {
                try {
                    runIsolated(step, killerCharacterId, monsterCode, work);
                } finally {
                    pending.remove(task);
                }
            });
        } catch (RejectedExecutionException e) {
            pending.remove(task);
            LOG.warn(
                    "Bỏ lượt trao thưởng hạ quái vì server đang tắt: step={} characterId={} monsterCode={}",
                    step,
                    killerCharacterId,
                    monsterCode);
        }
    }

    // PMD AvoidCatchingGenericException: ranh giới của một task nền, cùng lý do
    // CharacterPositionPersister.savePositionSafely. Thứ các cổng thật ném đều không checked và không
    // chung một kiểu hẹp hơn: DatabaseAccessException (JDBC), CharacterNotFoundException (nhân vật bị
    // xoá giữa chừng), IllegalStateException từ pool đã đóng. Bắt hẹp hơn là để lọt đúng loại lỗi chưa
    // ai nghĩ tới chết im trong Virtual Thread như cũ.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    static void runIsolated(Step step, long killerCharacterId, String monsterCode, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            // WARN, không ERROR (dòng 78(d), chủ dự án duyệt 2026-10-17): một phần thưởng mất vì DB chập
            // chờn là chuyện của MỘT người chơi, không phải sự cố cần đánh thức trực ban — cảnh báo theo
            // TẦN SUẤT (nhiều dòng này trong một khoảng) mới là tín hiệu thật.
            LOG.warn(
                    "Trao thưởng hạ quái thất bại — phần thưởng này MẤT, không thử lại:"
                            + " step={} characterId={} monsterCode={}",
                    step,
                    killerCharacterId,
                    monsterCode,
                    e);
        }
    }

    /**
     * Ngừng nhận việc mới rồi chờ việc đang dở xong (SERVER.md §10) — gọi sau khi mọi World đã dừng
     * tick, TRƯỚC khi đóng pool DB.
     *
     * <p><b>Chờ lượt đang dở xong (sửa 2026-10-02).</b> Bản cũ chỉ {@code shutdown()} rồi trả về
     * ngay, và {@code GatewayBootstrap} đóng pool DB ngay sau đó: rơi đồ/EXP/tiến độ quest của một
     * con quái vừa chết trước khi tắt bị cắt giữa chừng và mất vĩnh viễn — quái không được lưu, nên
     * không có gì để chạy lại lúc khởi động. Quá trần {@link ShutdownDrain#TIMEOUT_SECONDS} (DB treo)
     * thì vẫn đóng tiếp: thà mất vài phần thưởng còn hơn treo cả quá trình tắt (Docker SIGKILL sau
     * {@code stop_grace_period}).
     *
     * <p><b>Cắt thì nói ai bị cắt (dòng 78(b), chủ dự án duyệt 2026-10-17).</b> Chấp nhận cắt, không nâng
     * trần — nhưng một dòng WARN liệt kê từng lượt còn dở (bước, nhân vật, quái) để bù tay được. Lượt
     * bị cắt khi pool đóng có thể còn để lại dòng WARN "thất bại" của riêng nó, hoặc không (bị cắt ngay
     * giữa câu lệnh) — danh sách này là nguồn đáng tin.
     */
    void stop() {
        stop(ShutdownDrain.TIMEOUT_SECONDS * MILLIS_PER_SECOND);
    }

    /** {@link #stop()} với trần tuỳ chọn — cho test. */
    void stop(long timeoutMillis) {
        executor.shutdown();
        if (!ShutdownDrain.await(executor, "Trao thưởng hạ quái", timeoutMillis) && !pending.isEmpty()) {
            LOG.warn("Tắt server cắt ngang {} lượt trao thưởng hạ quái — cần bù tay: {}", pending.size(), pending);
        }
    }

    /** Số lượt đang dở — chỉ dùng để test. */
    int pendingCount() {
        return pending.size();
    }
}
