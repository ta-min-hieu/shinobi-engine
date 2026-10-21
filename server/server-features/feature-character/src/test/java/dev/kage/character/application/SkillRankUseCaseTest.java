package dev.kage.character.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.kage.character.domain.SkillRankPolicy;
import dev.kage.character.domain.SkillRankPort;

class SkillRankUseCaseTest {

    private static final long CHARACTER = 1L;
    private static final List<SkillRankUseCase.FactionSkill> HOA = List.of(
            new SkillRankUseCase.FactionSkill("fireball", 1),
            new SkillRankUseCase.FactionSkill("hoa_tia_lua", 1),
            new SkillRankUseCase.FactionSkill("hoa_bung_no", 1),
            new SkillRankUseCase.FactionSkill("hoa_diem_nguc", 15));
    private static final List<SkillRankUseCase.FactionSkill> THUY =
            List.of(new SkillRankUseCase.FactionSkill("thuy_song_du", 1));

    private final SkillRankUseCase useCase =
            new SkillRankUseCase(SkillRankPort.inMemory(), action -> action.accept(null));

    @Test
    void pointsShouldBeHalfTheLevel_andEachUpgradeSpendsOne() {
        assertThat(useCase.sheet(CHARACTER, 5, HOA).pointsAvailable()).isEqualTo(2);

        assertThat(useCase.upgrade(CHARACTER, "fireball", 5, HOA)).isEqualTo(SkillRankUseCase.Outcome.OK);
        assertThat(useCase.upgrade(CHARACTER, "fireball", 5, HOA)).isEqualTo(SkillRankUseCase.Outcome.OK);
        assertThat(useCase.upgrade(CHARACTER, "hoa_tia_lua", 5, HOA)).isEqualTo(SkillRankUseCase.Outcome.NO_POINTS);

        SkillRankUseCase.Sheet sheet = useCase.sheet(CHARACTER, 5, HOA);
        assertThat(sheet.pointsAvailable()).isZero();
        assertThat(sheet.skills().getFirst().rank()).isEqualTo(3);
    }

    @Test
    void shouldRefuseOtherFactionsSkills_lockedSkills_andMaxRank() {
        assertThat(useCase.upgrade(CHARACTER, "thuy_song_du", 40, HOA))
                .isEqualTo(SkillRankUseCase.Outcome.UNKNOWN_SKILL);
        assertThat(useCase.upgrade(CHARACTER, "hoa_diem_nguc", 14, HOA))
                .isEqualTo(SkillRankUseCase.Outcome.LEVEL_TOO_LOW);
        for (int i = 1; i < SkillRankPolicy.MAX_RANK; i++) {
            assertThat(useCase.upgrade(CHARACTER, "fireball", 40, HOA)).isEqualTo(SkillRankUseCase.Outcome.OK);
        }
        assertThat(useCase.upgrade(CHARACTER, "fireball", 40, HOA)).isEqualTo(SkillRankUseCase.Outcome.MAX_RANK);
    }

    @Test
    void changingFactionShouldGiveAFreshPool_andKeepOldRanks() {
        useCase.upgrade(CHARACTER, "fireball", 4, HOA);
        useCase.upgrade(CHARACTER, "fireball", 4, HOA);

        assertThat(useCase.sheet(CHARACTER, 4, THUY).pointsAvailable())
                .as("hệ mới: quỹ nguyên")
                .isEqualTo(2);
        assertThat(useCase.ranksOf(CHARACTER)).isEqualTo(Map.of("fireball", 3));
    }

    @Test
    void powerShouldGrowTenPercentPerRank() {
        assertThat(SkillRankPolicy.powerPercent(1)).isEqualTo(100);
        assertThat(SkillRankPolicy.powerPercent(5)).isEqualTo(140);
        assertThat(SkillRankPolicy.totalPoints(30)).isEqualTo(15);
    }
}
