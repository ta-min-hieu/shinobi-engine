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

    /**
     * Mỗi cấp cường hoá cộng thêm bao nhiêu PHẦN TRĂM chỉ số GỐC của chính món đó (ADR-036 D3) —
     * {@code +15} ⇒ ×2,5. Đặt ở ĐÂY, cạnh tỉ lệ và chi phí, để một lần cân bằng cường hoá chỉ phải
     * mở một file.
     *
     * <p>Vì sao phần trăm chứ không phải số tuyệt đối: phần trăm tự chia tỉ lệ theo bậc đồ — cùng
     * một công thức, {@code +10} trên kiếm gỗ vẫn là đồ tập sự, {@code +10} trên Katana Thép mới
     * đáng 20.000 vàng. Số tuyệt đối thì cường hoá món RẺ NHẤT luôn là nước đi tối ưu, và bậc đồ
     * mất hết ý nghĩa ở mức cường hoá cao (ADR-036, phương án D bị loại vì đúng lý do này).
     *
     * <p>0,10 là ĐỀ XUẤT khởi điểm CHƯA qua playtest, y như bảng tỉ lệ bên dưới. Chỉnh hệ số này
     * không cần mở lại ADR (Revisit Trigger của ADR-036).
     */
    public static final double ENHANCE_STEP = 0.10;

    private static final int[] SUCCESS_RATE_PERCENT = {95, 90, 85, 80, 70, 60, 50, 40, 30, 20, 15, 12, 10, 8, 5};
    private static final long[] GOLD_COST = {
        100, 200, 350, 550, 800, 1200, 1700, 2300, 3000, 4000, 5500, 7500, 10000, 14000, 20000
    };

    private EnhancementTable() {}

    /**
     * Chỉ số HIỆU DỤNG của một món sau khi tính cường hoá:
     * {@code bonus_gốc × (1 + ENHANCE_STEP × enhanceLevel)}, làm tròn về số nguyên gần nhất.
     *
     * <p>Trước ADR-036 KHÔNG có hàm này và không công thức nào đọc {@code enhance_level} — cường
     * hoá chỉ đổi một con số trong DB và một nhãn trong tên hiển thị, đo bằng chơi thật: nâng
     * {@code steel_katana} từ +0 lên +7 trừ 8.900 vàng mà chỉ số nhân vật KHÔNG đổi.
     *
     * @param baseBonus chỉ số gốc của món trong {@code item_template} (0 nghĩa là món không cộng
     *     chỉ số đó — cường hoá 0 vẫn ra 0, đúng: cường hoá KHUẾCH ĐẠI, không tạo chỉ số từ hư vô)
     * @param enhanceLevel cấp cường hoá hiện tại của MÓN ĐÓ, 0..{@link ItemStack#MAX_ENHANCE}
     */
    public static int enhancedBonus(int baseBonus, int enhanceLevel) {
        if (enhanceLevel < 0 || enhanceLevel > ItemStack.MAX_ENHANCE) {
            throw new IllegalArgumentException(
                    "enhanceLevel ngoài khoảng 0.." + ItemStack.MAX_ENHANCE + ": " + enhanceLevel);
        }
        if (baseBonus == 0 || enhanceLevel == 0) {
            return baseBonus;
        }
        return Math.toIntExact(Math.round(baseBonus * (1.0 + (ENHANCE_STEP * enhanceLevel))));
    }

    public static int successRatePercent(int currentLevel) {
        return SUCCESS_RATE_PERCENT[currentLevel];
    }

    public static long goldCost(int currentLevel) {
        return GOLD_COST[currentLevel];
    }
}
