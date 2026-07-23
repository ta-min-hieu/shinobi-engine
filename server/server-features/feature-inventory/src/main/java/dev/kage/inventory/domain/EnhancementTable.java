package dev.kage.inventory.domain;

/**
 * Bảng tỉ lệ thành công + chi phí gold cường hoá theo cấp hiện tại (+0→+15, GDD §3 "tỉ lệ giảm
 * dần"). Số liệu là ĐỀ XUẤT ban đầu (GDD chưa chốt số cụ thể) — chủ dự án duyệt qua GAMEPLAY.md §7,
 * chỉnh cân bằng ở đây khi cần, không cần đổi {@code EnhanceItemUseCase}.
 *
 * <p>Index mảng = {@code currentLevel} (0..14, ứng với nâng từ +0→+1 ... +14→+15).
 * {@code ItemStack.MAX_ENHANCE} (=15) chặn gọi với currentLevel=15 ở tầng UseCase trước khi tới đây.
 */
public final class EnhancementTable {

    /** Từ cấp này trở lên, thất bại làm RỚT 1 cấp trừ khi tiêu "Bảo Vệ" (GDD "Bảo Vệ chống rớt cấp
     * từ +10", cài ở lát cắt sau — {@link #PROTECTION_CHARM_CODE}). */
    public static final int DROP_ON_FAIL_FROM_LEVEL = 10;

    /** {@code item_template.code} của "Bảo Vệ" — item tiêu hao mua ở shop (GAMEPLAY.md §7.1), tra
     * qua {@code ItemTemplatePort.findIdByCode} thay vì hardcode ID số (ID phụ thuộc thứ tự seed). */
    public static final String PROTECTION_CHARM_CODE = "enhance_protection_charm";

    private static final int[] SUCCESS_RATE_PERCENT = {95, 90, 85, 80, 70, 60, 50, 40, 30, 20, 15, 12, 10, 8, 5};
    private static final long[] GOLD_COST = {100, 200, 350, 550, 800, 1200, 1700, 2300, 3000, 4000, 5500, 7500, 10000,
            14000, 20000};

    private EnhancementTable() {
    }

    public static int successRatePercent(int currentLevel) {
        return SUCCESS_RATE_PERCENT[currentLevel];
    }

    public static long goldCost(int currentLevel) {
        return GOLD_COST[currentLevel];
    }
}
