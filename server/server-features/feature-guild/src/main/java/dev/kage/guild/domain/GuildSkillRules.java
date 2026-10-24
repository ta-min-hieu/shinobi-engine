package dev.kage.guild.domain;

import java.util.Map;
import java.util.Optional;

/** Luật điểm kỹ năng bang (ADR-085 D2) — hàm thuần; adapter gọi DƯỚI khoá dòng bang. */
public final class GuildSkillRules {

    private GuildSkillRules() {}

    /** Tổng điểm của bang cấp {@code guildLevel}. */
    public static int totalPoints(int guildLevel) {
        return Math.max(0, guildLevel) * GuildSkill.POINTS_PER_GUILD_LEVEL;
    }

    /** Điểm đã tiêu = tổng bậc. */
    public static int spentPoints(Map<GuildSkill, Integer> ranks) {
        return ranks.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Lý do từ chối nâng, hoặc rỗng nếu nâng được. */
    public static Optional<GuildSkillOutcome> refusal(
            boolean leader, int guildLevel, Map<GuildSkill, Integer> ranks, GuildSkill skill) {
        if (!leader) {
            return Optional.of(GuildSkillOutcome.NOT_LEADER);
        }
        if (ranks.getOrDefault(skill, 0) >= GuildSkill.MAX_RANK) {
            return Optional.of(GuildSkillOutcome.MAX_RANK);
        }
        if (spentPoints(ranks) >= totalPoints(guildLevel)) {
            return Optional.of(GuildSkillOutcome.NO_POINTS);
        }
        return Optional.empty();
    }
}
