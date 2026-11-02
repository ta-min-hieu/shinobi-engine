package dev.kage.world.domain;

/**
 * Luật Tàng Bảo Đồ (ADR-147) — hàm thuần. Đào được khi đứng cách điểm chôn không quá {@value #DIG_REACH_METERS} m theo
 * trục X (di chuyển khoá trục X — ADR-033), cùng tầm với hái linh thảo (ADR-098).
 */
public final class TreasureRules {

    /** Mã vật phẩm Tàng Bảo Đồ (V119). */
    public static final String MAP_ITEM_CODE = "treasure_map";

    /** Tầm đào (m). */
    public static final float DIG_REACH_METERS = 2.5f;

    /** Quái canh kho hiện cách người đào chừng này mét — như quái kỳ ngộ (ADR-121 D4). */
    public static final float GUARDIAN_OFFSET_METERS = 4f;

    private TreasureRules() {}

    /** Khoảng cách theo trục X từ {@code x} tới điểm chôn. */
    public static float distance(float x, TreasurePort.Spot spot) {
        return Math.abs(x - spot.x());
    }

    /** Đứng đủ gần để đào chưa. */
    public static boolean withinReach(float x, TreasurePort.Spot spot) {
        return distance(x, spot) <= DIG_REACH_METERS;
    }
}
