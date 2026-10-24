package dev.kage.world.domain;

/**
 * Một điểm truyền tống trên world chính (ADR-100) — tham số đọc từ {@code waypoint}.
 *
 * @param alwaysOpen mở sẵn cho mọi người (tông môn), không cần tới gần lần đầu
 */
public record Waypoint(String code, String nameKey, float x, float y, int sortOrder, boolean alwaysOpen) {

    /** Bán kính quanh điểm để MỞ nó lần đầu (m). */
    public static final float UNLOCK_RADIUS_METERS = 4f;

    /** Đứng ở {@code x} có đủ gần để mở điểm này không (di chuyển khoá trục X — ADR-033). */
    public boolean unlocksAt(float positionX) {
        return Math.abs(positionX - x) <= UNLOCK_RADIUS_METERS;
    }
}
