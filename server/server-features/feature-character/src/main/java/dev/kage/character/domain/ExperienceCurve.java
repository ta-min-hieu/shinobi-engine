package dev.kage.character.domain;

/**
 * Đường cong kinh nghiệm — cần bao nhiêu EXP để lên từ cấp {@code n} sang {@code n+1}, và tổng EXP
 * tích luỹ ứng với một cấp.
 *
 * <p>⚠️ <b>Con số là PLACEHOLDER</b>, dựng 2026-08-09 khi chủ dự án hỏi "không có vạch kinh nghiệm
 * để biết bao giờ lên level à". Trước đó dự án CHƯA HỀ có khái niệm lên cấp: cột
 * {@code character.exp} (V2) tồn tại và quest có cộng EXP vào đó, nhưng không nơi nào đọc ra để đổi
 * thành {@code level} cả. Cân bằng thật (bao nhiêu quái một cấp, cấp tối đa bao nhiêu) là nội dung
 * GDD, chủ dự án chốt sau — sửa ở ĐÚNG một chỗ này.
 *
 * <p>Tuyến tính có chủ đích ({@code 100 × cấp hiện tại}): cấp 1→2 tốn 100, 2→3 tốn 200... Với quái
 * bù nhìn cho 20 EXP ({@code monster_template.exp_reward}) thì cấp đầu tiên mất 5 con — đủ
 * nhanh để chủ dự án THẤY vạch chạy và thấy lên cấp thật trong một lần chơi thử, đó mới là thứ
 * đang cần nghiệm thu. Cấp số nhân sẽ đúng "cảm giác MMO" hơn nhưng làm lần lên cấp đầu tiên biến
 * mất khỏi tầm với của một buổi test.
 */
public final class ExperienceCurve {

    /** EXP cần cho MỖI cấp, nhân với cấp hiện tại. */
    private static final long EXP_PER_LEVEL_STEP = 100L;

    /**
     * Cấp tối đa — chạm trần thì EXP vẫn cộng dồn vào {@code character.exp} nhưng không lên cấp
     * nữa, và {@code expForNextLevel} trả 0 để client vẽ vạch đầy.
     */
    public static final int MAX_LEVEL = 100;

    private ExperienceCurve() {}

    /**
     * EXP cần để đi từ {@code level} lên {@code level + 1}.
     *
     * @return 0 nếu đã ở {@link #MAX_LEVEL} (không còn cấp nào để lên).
     */
    public static long expForNextLevel(int level) {
        if (level >= MAX_LEVEL) {
            return 0L;
        }
        return EXP_PER_LEVEL_STEP * level;
    }

    /**
     * Quy tổng EXP tích luỹ ra trạng thái cấp hiện tại. Tính LẠI từ tổng thay vì cộng dồn từng
     * bước: {@code character.exp} là nguồn sự thật duy nhất đã có sẵn trong DB (quest cũng cộng
     * thẳng vào đó — {@code PostgresQuestExpAdapter}), nên suy cấp từ nó thì mọi nguồn EXP đều tự
     * động đúng, kể cả EXP đã tích trước khi có hệ thống lên cấp này.
     */
    public static ExperienceProgress progressOf(long totalExp) {
        long remaining = Math.max(0L, totalExp);
        int level = 1;
        while (level < MAX_LEVEL && remaining >= expForNextLevel(level)) {
            remaining -= expForNextLevel(level);
            level++;
        }
        return new ExperienceProgress(level, Math.max(0L, totalExp), remaining, expForNextLevel(level));
    }
}
