package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import dev.kage.guild.api.GuildSkillFacade;
import dev.kage.guild.domain.GuildSkill;
import dev.kage.guild.domain.GuildSkillOutcome;
import dev.kage.guild.domain.GuildSkillPort;
import dev.kage.guild.domain.GuildSkillRules;
import dev.kage.protocol.GuildSkillView;
import dev.kage.protocol.ListGuildSkillsResponse;
import dev.kage.protocol.UpgradeGuildSkillCommand;
import dev.kage.protocol.UpgradeGuildSkillOutcome;
import dev.kage.protocol.UpgradeGuildSkillResponse;

/** Hai lệnh kỹ năng bang (ADR-085) — tách khỏi {@code GatewayChannelHandler}. Chạy trên Virtual Thread (chạm DB). */
final class GuildSkillCommands {

    private static final Map<GuildSkillOutcome, UpgradeGuildSkillOutcome> OUTCOMES =
            new EnumMap<>(GuildSkillOutcome.class);

    static {
        OUTCOMES.put(GuildSkillOutcome.OK, UpgradeGuildSkillOutcome.UPGRADE_GUILD_SKILL_OUTCOME_OK);
        OUTCOMES.put(GuildSkillOutcome.NOT_IN_GUILD, UpgradeGuildSkillOutcome.UPGRADE_GUILD_SKILL_OUTCOME_NOT_IN_GUILD);
        OUTCOMES.put(GuildSkillOutcome.NOT_LEADER, UpgradeGuildSkillOutcome.UPGRADE_GUILD_SKILL_OUTCOME_NOT_LEADER);
        OUTCOMES.put(GuildSkillOutcome.MAX_RANK, UpgradeGuildSkillOutcome.UPGRADE_GUILD_SKILL_OUTCOME_MAX_RANK);
        OUTCOMES.put(GuildSkillOutcome.NO_POINTS, UpgradeGuildSkillOutcome.UPGRADE_GUILD_SKILL_OUTCOME_NO_POINTS);
    }

    private final GuildSkillFacade skills;

    GuildSkillCommands(GuildSkillFacade skills) {
        this.skills = skills;
    }

    ListGuildSkillsResponse list(long characterId) {
        ListGuildSkillsResponse.Builder response = ListGuildSkillsResponse.newBuilder();
        Optional<GuildSkillPort.GuildSkills> guild = skills.skillsOf(characterId);
        guild.ifPresent(found -> {
            int total = GuildSkillRules.totalPoints(found.guildLevel());
            response.setInGuild(true)
                    .setLeader(found.leader())
                    .setPointsTotal(total)
                    .setPointsAvailable(Math.max(0, total - GuildSkillRules.spentPoints(found.ranks())));
        });
        for (GuildSkill skill : GuildSkill.values()) {
            response.addSkills(GuildSkillView.newBuilder()
                    .setCode(skill.name().toLowerCase(Locale.ROOT))
                    .setRank(guild.map(found -> found.rankOf(skill)).orElse(0))
                    .setMaxRank(GuildSkill.MAX_RANK)
                    .setPerRank(skill.perRank()));
        }
        return response.build();
    }

    /** Nâng một bậc; {@code true} ở {@link Upgrade#changed()} khi chỉ số thành viên phải tính lại. */
    Upgrade upgrade(long characterId, UpgradeGuildSkillCommand command) {
        Optional<GuildSkill> skill = parse(command.getCode());
        if (skill.isEmpty()) {
            return new Upgrade(
                    UpgradeGuildSkillResponse.newBuilder()
                            .setOutcome(UpgradeGuildSkillOutcome.UPGRADE_GUILD_SKILL_OUTCOME_UNKNOWN_SKILL)
                            .setSheet(list(characterId))
                            .build(),
                    false);
        }
        GuildSkillOutcome outcome = skills.upgrade(characterId, skill.get());
        UpgradeGuildSkillResponse response = UpgradeGuildSkillResponse.newBuilder()
                .setOutcome(OUTCOMES.get(outcome))
                .setSheet(list(characterId))
                .build();
        return new Upgrade(response, outcome == GuildSkillOutcome.OK);
    }

    /** Trả lời kèm cờ "bang vừa đổi buff". */
    record Upgrade(UpgradeGuildSkillResponse response, boolean changed) {}

    private static Optional<GuildSkill> parse(String code) {
        for (GuildSkill skill : GuildSkill.values()) {
            if (skill.name().equalsIgnoreCase(code)) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }
}
