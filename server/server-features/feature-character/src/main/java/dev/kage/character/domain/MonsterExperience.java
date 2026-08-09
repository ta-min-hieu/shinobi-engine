package dev.kage.character.domain;

/**
 * EXP nhận được khi hạ gục một con quái, tra theo {@code monster_code}.
 *
 * <p>⚠️ <b>Hardcode có chủ đích</b>, cùng lý do {@code server-world.CombatConstants.MONSTER_STATS}
 * chưa đọc từ bảng {@code monster_template}: bảng đó chưa tồn tại. Khi nó ra đời, EXP phải là một
 * CỘT trong đó chứ không phải bảng tra Java — đây là chỗ tạm để vòng lặp "đánh quái → lên cấp"
 * chạy được thật (chủ dự án yêu cầu 2026-08-09), không phải thiết kế cuối.
 *
 * <p>Nằm ở {@code feature-character} chứ không phải {@code server-world} vì EXP là chuyện của nhân
 * vật, và {@code server-world} không được biết tới DB/persistence — nó chỉ báo "ai giết code gì"
 * qua {@code WorldOutboundSink#onMonsterKilled}.
 */
public final class MonsterExperience {

    /** Quái bù nhìn (50 HP) — 5 con là lên cấp 2, xem {@link ExperienceCurve} về lý do chọn nhanh. */
    public static final long TRAINING_DUMMY_EXP = 20L;

    /** Boss dungeon (320 HP, ~6 lần máu quái thường) — thưởng gấp 10, đáng công cả party. */
    public static final long SAMPLE_BOSS_EXP = 200L;

    private static final String TRAINING_DUMMY_CODE = "training_dummy";
    private static final String SAMPLE_BOSS_CODE = "sample_boss";

    private MonsterExperience() {}

    /**
     * @return EXP thưởng cho {@code monsterCode}; {@code 0} nếu code lạ — bỏ qua an toàn thay vì
     *     ném lỗi, một con quái mới chưa khai báo EXP không đáng làm hỏng lượt giết của người chơi.
     */
    public static long rewardFor(String monsterCode) {
        return switch (monsterCode) {
            case TRAINING_DUMMY_CODE -> TRAINING_DUMMY_EXP;
            case SAMPLE_BOSS_CODE -> SAMPLE_BOSS_EXP;
            default -> 0L;
        };
    }
}
