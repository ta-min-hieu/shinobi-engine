package dev.kage.inventory.domain;

/** Cấp linh thú (ADR-072 D2) — hàm thuần, tham số cân bằng ở một chỗ. */
public final class PetPolicy {

    public static final int MAX_LEVEL = 30;

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
        return new PetProgress(current.inventoryItemId(), current.itemTemplateId(), newLevel, exp, newLevel > level);
    }

    /** Cấp linh thú quy ra "cấp cường hoá" để nhân chỉ số gốc (cùng công thức +10%/cấp). */
    public static int bonusLevel(int petLevel) {
        return Math.max(0, petLevel - 1);
    }
}
