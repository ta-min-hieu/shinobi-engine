package dev.kage.friend.domain;

import java.util.List;

/** Sư đồ (ADR-093) — tham số cân bằng ở một chỗ, hàm thuần. Số là PLACEHOLDER cân bằng. */
public final class MentorshipPolicy {

    /** Sư phụ phải từ cấp này trở lên (Tử Phủ). */
    public static final int MIN_MENTOR_LEVEL = 40;

    /** Đồ đệ phải DƯỚI cấp này; chạm nó là xuất sư. */
    public static final int GRADUATION_LEVEL = 30;

    /** Một sư phụ nhận tối đa ngần này đồ đệ cùng lúc. */
    public static final int MAX_DISCIPLES = 3;

    /** Mốc cấp của đồ đệ có thư thưởng cho CẢ HAI, tăng dần; mốc cuối là {@link #GRADUATION_LEVEL}. */
    public static final List<Integer> MILESTONES = List.of(10, 20, GRADUATION_LEVEL);

    /** Linh thạch đồ đệ nhận ở một mốc = mốc × hệ số này. */
    public static final long DISCIPLE_GOLD_PER_LEVEL = 50L;

    /** Linh thạch sư phụ nhận ở một mốc = mốc × hệ số này — dạy dỗ đáng công hơn. */
    public static final long MENTOR_GOLD_PER_LEVEL = 100L;

    private MentorshipPolicy() {}

    /** Mọi mốc {@code <= level} — cấp có thể nhảy qua nhiều mốc một lúc (thưởng quest). */
    public static List<Integer> milestonesReachedAt(int level) {
        return MILESTONES.stream().filter(milestone -> milestone <= level).toList();
    }

    /** Lời bái sư hợp lệ về CẤP không — {@link MentorshipOutcome#OK} nếu hợp lệ, không thì lý do. */
    public static MentorshipOutcome checkLevels(int discipleLevel, int mentorLevel) {
        if (discipleLevel >= GRADUATION_LEVEL) {
            return MentorshipOutcome.DISCIPLE_LEVEL_TOO_HIGH;
        }
        if (mentorLevel < MIN_MENTOR_LEVEL) {
            return MentorshipOutcome.MENTOR_LEVEL_TOO_LOW;
        }
        return MentorshipOutcome.OK;
    }
}
