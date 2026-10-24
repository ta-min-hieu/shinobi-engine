package dev.kage.inventory.domain;

/** Cấp (ADR-072 D2) + bậc tiến hoá (ADR-078) linh thú — hàm thuần, tham số cân bằng ở một chỗ. */
public final class PetPolicy {

    public static final int MAX_LEVEL = 30;

    /** Cấp tối thiểu để tiến hoá bằng Linh Thú Đan (ADR-078). */
    public static final int EVOLVE_LEVEL = 20;

    /** Bậc tiến hoá cao nhất — bản đầu chỉ một lần tiến hoá. */
    public static final int MAX_STAGE = 2;

    /** Chỉ số linh thú bậc 2 nhân chừng này phần trăm (bậc 1 = 100). */
    public static final int EVOLVED_STAT_PERCENT = 150;

    private static final int PERCENT = 100;

    /** Mỗi cấp linh thú trên 1 cộng chừng này phần trăm chỉ số gốc — bằng bước cường hoá (ADR-072 D2). */
    private static final int LEVEL_STEP_PERCENT = 10;

    private PetPolicy() {}

    /** EXP cần để từ cấp {@code level} lên cấp kế; 0 nếu đã tối đa. */
    public static long expToNext(int level) {
        return level >= MAX_LEVEL ? 0L : 100L * level;
    }

    /** Cộng {@code amount} EXP vào linh thú {@code current}. Tới cấp tối đa thì EXP dư bỏ đi (giữ 0). */
    public static PetProgress gain(PetProgress current, long amount) {
        int level = current.level();
        int newLevel = level;
        long exp = current.expIntoLevel() + Math.max(0L, amount);
        while (newLevel < MAX_LEVEL && exp >= expToNext(newLevel)) {
            exp -= expToNext(newLevel);
            newLevel++;
        }
        if (newLevel >= MAX_LEVEL) {
            exp = 0L;
        }
        return new PetProgress(
                current.inventoryItemId(), current.itemTemplateId(), newLevel, exp, newLevel > level, current.stage());
    }

    /** Chỉ số {@code value} (đã nhân cấp) sau hệ số bậc tiến hoá, làm tròn xuống (ADR-078). */
    public static int staged(int value, int stage) {
        return stage >= MAX_STAGE ? value * EVOLVED_STAT_PERCENT / PERCENT : value;
    }

    /**
     * Chỉ số linh thú: gốc × (1 + 10% × (cấp − 1)) (ADR-072 D2, cùng công thức cường hoá nhưng KHÔNG chặn ở +15 —
     * cấp linh thú tới {@link #MAX_LEVEL}), rồi nhân bậc tiến hoá (ADR-078). Làm tròn nửa-lên như cường hoá.
     */
    public static int petBonus(int base, int petLevel, int stage) {
        int steps = Math.clamp(petLevel, 1, MAX_LEVEL) - 1;
        long scaledHundredths = (long) base * (PERCENT + LEVEL_STEP_PERCENT * steps);
        return staged(Math.toIntExact(Math.floorDiv(scaledHundredths + PERCENT / 2, PERCENT)), stage);
    }
}
