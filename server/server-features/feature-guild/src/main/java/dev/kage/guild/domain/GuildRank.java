package dev.kage.guild.domain;

import java.util.List;

/**
 * Một bang trên bảng xếp hạng (ADR-068 D4) — đã sắp theo kinh nghiệm giảm dần.
 *
 * @param memberIds để tra "hạng bang của tôi"
 */
public record GuildRank(long guildId, String name, long leaderCharacterId, int level, long exp, List<Long> memberIds) {

    public GuildRank {
        memberIds = List.copyOf(memberIds);
    }
}
