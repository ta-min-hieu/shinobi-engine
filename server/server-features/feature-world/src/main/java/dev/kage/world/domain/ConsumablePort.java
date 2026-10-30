package dev.kage.world.domain;

/**
 * Tiêu / hoàn MỘT vật phẩm theo mã trong túi người chơi (lô 23 — Hồi Hồn Đan ADR-117, và các bùa/hạt giống sau đó).
 * Hiện thực ở server-database, dưới khoá túi đồ chung.
 */
public interface ConsumablePort {

    /** {@code true} nếu đã trừ đúng một cái. */
    boolean consume(long characterId, String itemCode);

    /** Trả lại một cái (lệnh sau đó không thành). */
    void refund(long characterId, String itemCode);
}
