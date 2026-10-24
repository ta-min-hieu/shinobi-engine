package dev.kage.guild.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** ADR-085 — 2 điểm mỗi cấp bang, chỉ bang chủ nâng, bậc tối đa 4. */
class GuildSkillRulesTest {

    @Test
    void shouldRefuseNonLeaders_maxRank_andWhenPointsRunOut() {
        assertThat(GuildSkillRules.refusal(false, 5, Map.of(), GuildSkill.VITALITY))
                .contains(GuildSkillOutcome.NOT_LEADER);
        assertThat(GuildSkillRules.refusal(true, 5, Map.of(GuildSkill.VITALITY, 4), GuildSkill.VITALITY))
                .contains(GuildSkillOutcome.MAX_RANK);
        assertThat(GuildSkillRules.refusal(true, 1, Map.of(GuildSkill.INSIGHT, 2), GuildSkill.VITALITY))
                .contains(GuildSkillOutcome.NO_POINTS);
        assertThat(GuildSkillRules.refusal(true, 2, Map.of(GuildSkill.INSIGHT, 2), GuildSkill.VITALITY))
                .isEmpty();
        assertThat(GuildSkillRules.totalPoints(5)).isEqualTo(10);
    }
}
