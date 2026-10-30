package dev.kage.world.domain;

import java.util.random.RandomGenerator;

/**
 * Luật linh điền (ADR-119) — hàm thuần. Mỗi người khởi đầu {@value #STARTING_PLOTS} ô, mở thêm tới {@value #MAX_PLOTS}
 * ô bằng linh thạch; ô được bạn bè tưới thì sản lượng kỳ vọng +{@value #WATER_BONUS_PERCENT}% (phần lẻ quyết bằng ngẫu
 * nhiên của server — thu hoạch nhỏ vẫn nhận đúng +10% về trung bình, không bị làm tròn mất).
 */
public final class SpiritFieldRules {

    public static final int STARTING_PLOTS = 4;
    public static final int MAX_PLOTS = 8;
    public static final int WATER_BONUS_PERCENT = 10;

    /** Giá mở ô thứ {@code STARTING_PLOTS + 1} — mỗi ô sau đắt thêm chừng này. */
    public static final long UNLOCK_COST_STEP = 500L;

    private static final int PERCENT = 100;

    private SpiritFieldRules() {}

    /** Giá mở thêm một ô khi đang có {@code plots} ô. */
    public static long unlockCost(int plots) {
        return UNLOCK_COST_STEP * (plots - STARTING_PLOTS + 2);
    }

    /** Sản lượng một ô; được tưới ⇒ ×1,1 với phần lẻ thành +1 theo xác suất. */
    public static int harvestYield(int baseQuantity, boolean watered, RandomGenerator random) {
        if (!watered) {
            return baseQuantity;
        }
        int scaled = baseQuantity * (PERCENT + WATER_BONUS_PERCENT);
        int whole = scaled / PERCENT;
        int remainder = scaled % PERCENT;
        return random.nextInt(PERCENT) < remainder ? whole + 1 : whole;
    }
}
