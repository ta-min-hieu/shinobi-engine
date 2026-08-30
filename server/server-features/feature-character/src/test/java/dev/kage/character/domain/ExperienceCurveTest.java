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

    /** EXP âm không tồn tại (cột DB có ràng buộc), nhưng vào tay dữ liệu hỏng thì phải là cấp 1. */
    @Test
    void shouldTreatNegativeExperienceAsZero() {
        assertThat(ExperienceCurve.progressOf(-5L).level()).isEqualTo(1);
    }
}
