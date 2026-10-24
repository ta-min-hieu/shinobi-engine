package dev.kage.guild.api;

import java.util.Optional;

import dev.kage.guild.domain.GuildSkill;
import dev.kage.guild.domain.GuildSkillOutcome;
import dev.kage.guild.domain.GuildSkillPort;

/** API kỹ năng bang (ADR-085). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class GuildSkillFacade {

    /** Composition không có kỹ năng bang (test) — không ai ở bang nào. */
    public static final GuildSkillFacade DISABLED = new GuildSkillFacade(new GuildSkillPort() {
        @Override
        public Optional<GuildSkills> skillsOf(long characterId) {
            return Optional.empty();
        }

        @Override
        public GuildSkillOutcome upgrade(long leaderId, GuildSkill skill) {
            return GuildSkillOutcome.NOT_IN_GUILD;
        }
    });

    private final GuildSkillPort port;

    /** Dựng ở composition root. */
    public GuildSkillFacade(GuildSkillPort port) {
        this.port = port;
    }

    /** Kỹ năng bang của bang người này; rỗng nếu không ở bang nào. */
    public Optional<GuildSkillPort.GuildSkills> skillsOf(long characterId) {
        return port.skillsOf(characterId);
    }

    /** Bang chủ nâng một bậc. */
    public GuildSkillOutcome upgrade(long leaderId, GuildSkill skill) {
        return port.upgrade(leaderId, skill);
    }
}
