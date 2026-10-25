package dev.kage.server.world;

/**
 * Ai nghe đoàn hộ tống đổi (ADR-114) — gọi TRÊN tick thread, phải trả về ngay (việc nặng sang luồng khác). Chỉ world
 * chính nối, qua {@link WorldFacade#useEscortListener}; tách khỏi {@link WorldOutboundSink} để sink của gateway (đúng
 * trần độ phức tạp cả lớp) không phải thêm phương thức.
 */
@FunctionalInterface
public interface EscortListener {

    /** Không ai nghe. */
    EscortListener NONE = snapshot -> {};

    /**
     * Đoàn của {@code snapshot.characterId()} vừa đổi (vị trí mỗi giây, máu, trạng thái). Trạng thái cuối
     * ({@code ARRIVED}/{@code FAILED}/{@code ABANDONED}) tới đúng một lần rồi đoàn biến mất khỏi World.
     */
    void onEscortChanged(WorldOutboundSink.EscortSnapshot snapshot);
}
