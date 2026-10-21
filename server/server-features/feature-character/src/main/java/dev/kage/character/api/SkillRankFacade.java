package dev.kage.character.api;

import java.util.List;
import java.util.Map;

import dev.kage.character.application.SkillRankUseCase;
import dev.kage.character.domain.SkillRankPort;

/** API điểm công pháp (ADR-071). */
public final class SkillRankFacade {

    /** Bậc trong bộ nhớ — composition test. */
    public static SkillRankFacade inMemory() {
        return new SkillRankFacade(new SkillRankUseCase(SkillRankPort.inMemory(), action -> action.accept(null)));
    }

    private final SkillRankUseCase useCase;

    /** Dựng ở composition root. */
    public SkillRankFacade(SkillRankUseCase useCase) {
        this.useCase = useCase;
    }

    /** Bậc theo mã chiêu (vắng = bậc 1). */
    public Map<String, Integer> ranksOf(long characterId) {
        return useCase.ranksOf(characterId);
    }

    /** Bậc chiêu của linh căn hiện tại + điểm còn. */
    public SkillRankUseCase.Sheet sheet(long characterId, int level, List<SkillRankUseCase.FactionSkill> skills) {
        return useCase.sheet(characterId, level, skills);
    }

    /** Nâng một bậc — kiểm linh căn, cấp, bậc tối đa, điểm. */
    public SkillRankUseCase.Outcome upgrade(
            long characterId, String skillCode, int level, List<SkillRankUseCase.FactionSkill> skills) {
        return useCase.upgrade(characterId, skillCode, level, skills);
    }
}
