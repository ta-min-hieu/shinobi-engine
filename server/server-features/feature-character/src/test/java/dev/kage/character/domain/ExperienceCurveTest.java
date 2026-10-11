package dev.kage.character.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Đường cong EXP (2026-08-09) — cấp luôn được SUY LẠI từ tổng EXP, không cộng dồn từng bước. */
class ExperienceCurveTest {

    @Test
    void shouldStartAtLevelOne_whenNoExperience() {
        ExperienceProgress progress = ExperienceCurve.progressOf(0L);

        assertThat(progress.level()).isEqualTo(1);
        assertThat(progress.expIntoLevel()).isZero();
        assertThat(progress.expForNextLevel()).isEqualTo(100L);
    }

    @Test
    void shouldReportProgressWithinLevel() {
        ExperienceProgress progress = ExperienceCurve.progressOf(60L);

        assertThat(progress.level()).isEqualTo(1);
        assertThat(progress.expIntoLevel()).isEqualTo(60L);
        assertThat(progress.totalExp()).isEqualTo(60L);
    }

    @Test
    void shouldLevelUp_whenThresholdReachedExactly() {
        ExperienceProgress progress = ExperienceCurve.progressOf(100L);

        assertThat(progress.level()).isEqualTo(2);
        assertThat(progress.expIntoLevel()).isZero();
        assertThat(progress.expForNextLevel()).isEqualTo(200L); // 100 × cấp hiện tại
    }

    /**
     * EXP tích luỹ TRƯỚC khi có hệ thống lên cấp (quest đã cộng vào {@code character.exp} từ Phase
     * 6) phải tự nhảy đúng cấp — đó là lý do suy lại từ tổng thay vì cộng dồn.
     */
    @Test
    void shouldJumpMultipleLevels_whenLargeTotalExperienceAlreadyStored() {
        // 100 + 200 + 300 = 600 đủ cho cấp 4, dư 50.
        ExperienceProgress progress = ExperienceCurve.progressOf(650L);

        assertThat(progress.level()).isEqualTo(4);
        assertThat(progress.expIntoLevel()).isEqualTo(50L);
    }

    @Test
    void shouldClampAtMaxLevel_andReportNoNextLevel() {
        ExperienceProgress progress = ExperienceCurve.progressOf(Long.MAX_VALUE / 2);

        assertThat(progress.level()).isEqualTo(ExperienceCurve.MAX_LEVEL);
        assertThat(progress.expForNextLevel()).isZero();
    }

    /** Biên ±1 quanh mốc đầu tiên: 99 chưa lên, 100 vừa lên, 101 lên và dư 1. */
    @Test
    void shouldCrossThresholdExactlyAtTheBoundary() {
        assertThat(ExperienceCurve.progressOf(99L).level()).isEqualTo(1);
        assertThat(ExperienceCurve.progressOf(100L).level()).isEqualTo(2);
        assertThat(ExperienceCurve.progressOf(101L).level()).isEqualTo(2);
        assertThat(ExperienceCurve.progressOf(101L).expIntoLevel()).isEqualTo(1L);
    }

    /** Biên ±1 quanh TRẦN: tổng EXP của cấp 100 là 100 × (1 + 2 + … + 99) = 495.000. */
    @Test
    void shouldReachMaxLevelExactlyAtItsThreshold_andNeverPassIt() {
        long expForMaxLevel = totalExpToReach(ExperienceCurve.MAX_LEVEL);

        assertThat(expForMaxLevel).isEqualTo(495_000L);
        assertThat(ExperienceCurve.progressOf(expForMaxLevel - 1L).level()).isEqualTo(ExperienceCurve.MAX_LEVEL - 1);
        assertThat(ExperienceCurve.progressOf(expForMaxLevel).level()).isEqualTo(ExperienceCurve.MAX_LEVEL);
        assertThat(ExperienceCurve.progressOf(expForMaxLevel + 1L).level()).isEqualTo(ExperienceCurve.MAX_LEVEL);
    }

    /** Tổng EXP lớn nhất mà cột BIGINT chứa được: không tràn, không âm, không vượt trần. */
    @Test
    void shouldNotOverflow_atTheLargestStorableTotal() {
        ExperienceProgress progress = ExperienceCurve.progressOf(Long.MAX_VALUE);

        assertThat(progress.level()).isEqualTo(ExperienceCurve.MAX_LEVEL);
        assertThat(progress.totalExp()).isEqualTo(Long.MAX_VALUE);
        assertThat(progress.expIntoLevel()).isEqualTo(Long.MAX_VALUE - totalExpToReach(ExperienceCurve.MAX_LEVEL));
        assertThat(progress.expForNextLevel()).isZero();
    }

    /** Mỗi mốc cấp 2..100: đúng tổng thì vừa lên, thiếu 1 thì chưa — không lệch một ở bất kỳ cấp nào. */
    @Test
    void shouldAgreeWithTheCumulativeTable_atEveryLevelBoundary() {
        for (int level = 2; level <= ExperienceCurve.MAX_LEVEL; level++) {
            long threshold = totalExpToReach(level);

            assertThat(ExperienceCurve.progressOf(threshold).level()).isEqualTo(level);
            assertThat(ExperienceCurve.progressOf(threshold).expIntoLevel()).isZero();
            assertThat(ExperienceCurve.progressOf(threshold - 1L).level()).isEqualTo(level - 1);
        }
    }

    private static long totalExpToReach(int level) {
        long total = 0L;
        for (int current = 1; current < level; current++) {
            total += ExperienceCurve.expForNextLevel(current);
        }
        return total;
    }

    /**
     * EXP âm không được phép tồn tại, nhưng vào tay dữ liệu hỏng thì phải là cấp 1. (Cột
     * {@code character.exp} có CHECK từ V52; trước đó thứ chặn chỉ là {@code Character} và {@code give_exp}.)
     */
    @Test
    void shouldTreatNegativeExperienceAsZero() {
        assertThat(ExperienceCurve.progressOf(-5L).level()).isEqualTo(1);
    }
}
