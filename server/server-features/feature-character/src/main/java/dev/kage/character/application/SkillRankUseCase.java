package dev.kage.character.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.character.domain.SkillRankPolicy;
import dev.kage.character.domain.SkillRankPort;
import dev.kage.character.domain.TransactionRunner;

/**
 * Nâng bậc chiêu bằng điểm công pháp (ADR-071). Danh sách chiêu của linh căn hiện tại do gateway đưa vào (nó
 * sống trong server-world, module này không biết) — {@code characterId} và cấp đều từ server, không từ gói.
 */
public final class SkillRankUseCase {

    /**
     * Một chiêu của linh căn hiện tại.
     *
     * @param code mã chiêu
     * @param requiredLevel cấp mở chiêu
     */
    public record FactionSkill(String code, int requiredLevel) {}

    /** Một dòng của cửa sổ Công pháp. */
    public record SkillRankView(String code, int rank, int requiredLevel) {}

    /** Toàn cảnh: bậc từng chiêu + điểm. */
    public record Sheet(List<SkillRankView> skills, int pointsTotal, int pointsAvailable) {

        public Sheet {
            skills = List.copyOf(skills);
        }
    }

    /** Kết quả nâng. */
    public enum Outcome {
        OK,
        UNKNOWN_SKILL,
        LEVEL_TOO_LOW,
        MAX_RANK,
        NO_POINTS
    }

    private final SkillRankPort port;
    private final TransactionRunner transactionRunner;

    public SkillRankUseCase(SkillRankPort port, TransactionRunner transactionRunner) {
        this.port = port;
        this.transactionRunner = transactionRunner;
    }

    /** Bậc theo mã chiêu (chiêu chưa nâng vắng mặt = bậc 1). */
    public Map<String, Integer> ranksOf(long characterId) {
        return port.ranksOf(characterId);
    }

    public Sheet sheet(long characterId, int level, List<FactionSkill> skills) {
        return sheetOf(port.ranksOf(characterId), level, skills);
    }

    /** Nâng {@code skillCode} một bậc — đếm điểm dưới khoá dòng nhân vật. */
    public Outcome upgrade(long characterId, String skillCode, int level, List<FactionSkill> skills) {
        Optional<FactionSkill> target =
                skills.stream().filter(skill -> skill.code().equals(skillCode)).findFirst();
        if (target.isEmpty()) {
            return Outcome.UNKNOWN_SKILL;
        }
        if (level < target.get().requiredLevel()) {
            return Outcome.LEVEL_TOO_LOW;
        }
        Outcome[] outcome = {Outcome.OK};
        transactionRunner.inTransaction(tx -> {
            Map<String, Integer> ranks = port.lockRanks(tx, characterId);
            int rank = ranks.getOrDefault(skillCode, 1);
            if (rank >= SkillRankPolicy.MAX_RANK) {
                outcome[0] = Outcome.MAX_RANK;
                return;
            }
            if (sheetOf(ranks, level, skills).pointsAvailable() <= 0) {
                outcome[0] = Outcome.NO_POINTS;
                return;
            }
            port.setRank(tx, characterId, skillCode, rank + 1);
        });
        return outcome[0];
    }

    private static Sheet sheetOf(Map<String, Integer> ranks, int level, List<FactionSkill> skills) {
        List<SkillRankView> views = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (FactionSkill skill : skills) {
            views.add(new SkillRankView(skill.code(), ranks.getOrDefault(skill.code(), 1), skill.requiredLevel()));
            codes.add(skill.code());
        }
        int total = SkillRankPolicy.totalPoints(level);
        int available = Math.max(0, total - SkillRankPolicy.spentPoints(ranks, codes));
        return new Sheet(views, total, available);
    }
}
