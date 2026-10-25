package dev.kage.inventory.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Luật tẩy luyện (ADR-111 D2) — hàm thuần; số ngẫu nhiên chỉ sinh ở server và đưa vào từ ngoài. */
public final class RefinementPolicy {

    /** Mã vật phẩm Tẩy Luyện Thạch. */
    public static final String STONE_CODE = "refine_stone";

    /** Đá mỗi lần tẩy không khoá dòng. */
    public static final int STONE_COST = 1;

    /** Đá mỗi lần tẩy có khoá MỘT dòng. */
    public static final int LOCKED_STONE_COST = 3;

    /** Số dòng tối thiểu / tối đa gieo lần đầu. */
    public static final int MIN_LINES = 1;

    public static final int MAX_LINES = 3;

    /** {@code lockedLine} mang giá trị này = không khoá dòng nào. */
    public static final int NO_LOCK = -1;

    private static final int ATK_BASE = 2;
    private static final int HP_BASE = 10;
    private static final int LEVELS_PER_POINT = 10;

    /** Chỉ số một dòng có thể mang. */
    public enum Stat {
        ATK,
        DEF,
        RES,
        HP
    }

    /**
     * Một dòng chỉ số phụ.
     *
     * @param index vị trí dòng 0..2
     */
    public record Line(int index, Stat stat, int value) {}

    private RefinementPolicy() {}

    /** Ô có chỉ số — tẩy được (không phi kiếm, không linh thú). */
    public static boolean refinable(EquipmentSlotType slot) {
        return slot != EquipmentSlotType.MOUNT && slot != EquipmentSlotType.PET;
    }

    /** Giá đá của một lần tẩy. */
    public static int stoneCost(int lockedLine) {
        return lockedLine == NO_LOCK ? STONE_COST : LOCKED_STONE_COST;
    }

    /** Trần một dòng theo chỉ số + cấp yêu cầu của món. */
    public static int maxValue(Stat stat, int requiredLevel) {
        return stat == Stat.HP ? HP_BASE + requiredLevel : ATK_BASE + requiredLevel / LEVELS_PER_POINT;
    }

    /** Sàn một dòng. */
    public static int minValue(Stat stat) {
        return stat == Stat.HP ? HP_BASE / 2 : 1;
    }

    /**
     * Dòng sau một lần tẩy. Chưa có dòng nào ⇒ gieo 1..3 dòng; có rồi ⇒ gieo lại mọi dòng trừ {@code lockedLine}, số
     * dòng giữ nguyên.
     */
    public static List<Line> reroll(List<Line> current, int lockedLine, int requiredLevel, RandomGenerator random) {
        int count = current.isEmpty() ? random.nextInt(MIN_LINES, MAX_LINES + 1) : current.size();
        List<Line> next = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (i == lockedLine) {
                next.add(current.get(i));
            } else {
                next.add(rollLine(i, requiredLevel, random));
            }
        }
        return List.copyOf(next);
    }

    private static Line rollLine(int index, int requiredLevel, RandomGenerator random) {
        Stat[] stats = Stat.values();
        Stat stat = stats[random.nextInt(stats.length)];
        return new Line(index, stat, random.nextInt(minValue(stat), maxValue(stat, requiredLevel) + 1));
    }

    /** Cộng phẳng các dòng (D3). */
    public static EquipmentBonus bonusOf(List<Line> lines) {
        Map<Stat, Integer> sums = new EnumMap<>(Stat.class);
        for (Line line : lines) {
            sums.merge(line.stat(), line.value(), Integer::sum);
        }
        return new EquipmentBonus(
                sums.getOrDefault(Stat.ATK, 0),
                sums.getOrDefault(Stat.DEF, 0),
                sums.getOrDefault(Stat.RES, 0),
                sums.getOrDefault(Stat.HP, 0));
    }
}
