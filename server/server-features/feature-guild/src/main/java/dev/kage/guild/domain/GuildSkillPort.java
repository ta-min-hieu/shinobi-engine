package dev.kage.guild.domain;

import java.util.Map;
import java.util.Optional;

/** Kỹ năng bang (ADR-085) — hiện thực ở server-database ({@code guild_skill}, V81). */
public interface GuildSkillPort {

    /**
     * Bang của một nhân vật nhìn từ kỹ năng bang.
     *
     * @param guildLevel cấp bang
     * @param leader người hỏi là bang chủ
     * @param ranks bậc từng kỹ năng (vắng = 0)
     */
    record GuildSkills(int guildLevel, boolean leader, Map<GuildSkill, Integer> ranks) {

        public GuildSkills {
            ranks = Map.copyOf(ranks);
        }

        /** Bậc của {@code skill} (0 nếu chưa nâng). */
        public int rankOf(GuildSkill skill) {
            return ranks.getOrDefault(skill, 0);
        }
    }

    /** Kỹ năng bang của bang {@code characterId} đang ở; rỗng nếu không ở bang nào. */
    Optional<GuildSkills> skillsOf(long characterId);

    /**
     * Nâng {@code skill} một bậc cho bang của {@code leaderId} — nguyên tử, kiểm bang chủ + điểm dưới khoá dòng bang.
     */
    GuildSkillOutcome upgrade(long leaderId, GuildSkill skill);
}
