package dev.kage.character.domain;

/**
 * Luật Thiên Kiếp (ADR-126) — hàm thuần. Tầng 9 của năm cảnh giới đầu (cấp 18, 36, 54, 72, 90 — mốc của
 * {@code CultivationRealm} ở client, ADR-038 D2) là trần: chưa độ kiếp thì EXP vẫn tích nhưng không lên cấp. Kiếp thứ
 * {@code k} (đếm từ 0 = số kiếp đã qua) giáng {@code 3 + k} đợt lôi kích, mỗi đợt {@value #STRIKE_PERCENT}% máu tối đa,
 * cách nhau {@value #STRIKE_INTERVAL_SECONDS} giây; sống qua đợt cuối là thắng.
 */
public final class TribulationRules {

    /** Số kiếp cả đời (năm cảnh giới đầu); qua hết thì trần là cấp tối đa. */
    public static final int TRIBULATIONS = 5;

    /** Bề rộng một cảnh giới (cấp). */
    public static final int LEVELS_PER_REALM = 18;

    public static final int BASE_WAVES = 3;
    public static final int STRIKE_PERCENT = 20;
    public static final int STRIKE_INTERVAL_SECONDS = 6;

    /** Thua: mất chừng này phần trăm lượng EXP cần cho cấp kế (không bao giờ tụt xuống dưới mốc cấp hiện tại). */
    public static final int FAIL_EXP_PERCENT = 10;

    /** Thưởng vĩnh viễn MỖI kiếp đã qua. */
    public static final int BONUS_ATK = 3;

    public static final int BONUS_DEF = 3;
    public static final int BONUS_RES = 3;
    public static final int BONUS_HP = 30;

    private static final int PERCENT = 100;

    private TribulationRules() {}

    /** Trần cấp khi đã qua {@code passed} kiếp. */
    public static int levelCap(int passed) {
        return passed >= TRIBULATIONS ? ExperienceCurve.MAX_LEVEL : LEVELS_PER_REALM * (passed + 1);
    }

    /** Số đợt lôi kích của kiếp kế tiếp. */
    public static int waves(int passed) {
        return BASE_WAVES + passed;
    }

    /** Sẵn sàng độ kiếp: đang đứng ở trần và EXP đã đủ cho cấp kế. */
    public static boolean ready(int passed, long totalExp) {
        int cap = levelCap(passed);
        return passed < TRIBULATIONS && ExperienceCurve.progressOf(totalExp).level() > cap;
    }

    /** EXP bị trừ khi thua kiếp ở trần {@code cap}. */
    public static long failPenalty(int cap) {
        return ExperienceCurve.expForNextLevel(cap) * FAIL_EXP_PERCENT / PERCENT;
    }

    /** Tổng EXP để đứng ở đầu cấp {@code level} — sàn của hình phạt. */
    public static long expToReach(int level) {
        long total = 0L;
        for (int l = 1; l < level; l++) {
            total += ExperienceCurve.expForNextLevel(l);
        }
        return total;
    }
}
