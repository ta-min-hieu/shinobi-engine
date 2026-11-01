package dev.kage.world.domain;

/**
 * ADR-137 — một dải PvP tự do trên trục X của một world (V112, bảng {@code pvp_zone}). Hai người chơi cùng đứng trong
 * dải và ngoài mọi vùng an toàn thì đánh nhau được. Chỉ trục X: world chính khoá di chuyển vào trục X (ADR-033).
 */
public record PvpZone(String code, String worldCode, float minX, float maxX) {

    public PvpZone {
        if (!(minX < maxX)) {
            throw new IllegalArgumentException("pvp_zone " + code + ": min_x phải nhỏ hơn max_x");
        }
    }
}
