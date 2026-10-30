package dev.kage.server.world;

/**
 * Người nghe "nhân vật vừa gục, đang chờ chọn cách hồi sinh" (ADR-117) — nối riêng cho world chính
 * ({@link WorldFacade#useRevivalListener}), không thêm phương thức vào sink của gateway. Gọi TRÊN tick
 * thread — phải trả về ngay.
 */
@FunctionalInterface
public interface RevivalListener {

    /** Không ai nghe (World phụ, test). */
    RevivalListener NONE = (characterId, seconds, canReviveInPlace) -> {};

    /**
     * Nhân vật vừa gục và đang nằm chờ chọn.
     *
     * @param seconds số giây trước khi tự về điểm hồi sinh
     * @param canReviveInPlace {@code false} nếu bị người chơi hạ (PvP)
     */
    void onAwaitingRevival(long characterId, int seconds, boolean canReviveInPlace);
}
