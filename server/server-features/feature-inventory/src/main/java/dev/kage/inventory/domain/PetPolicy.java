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

    /** Cấp linh thú quy ra "cấp cường hoá" để nhân chỉ số gốc (cùng công thức +10%/cấp). */
    public static int bonusLevel(int petLevel) {
        return Math.max(0, petLevel - 1);
    }
}
