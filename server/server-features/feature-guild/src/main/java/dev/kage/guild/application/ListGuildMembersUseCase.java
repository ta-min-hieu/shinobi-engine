package dev.kage.guild.application;

import java.util.List;

import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;

/** Danh sách thành viên bang của {@code characterId} — rỗng nếu chưa vào bang nào. */
public final class ListGuildMembersUseCase {

    private final GuildPort port;

    public ListGuildMembersUseCase(GuildPort port) {
        this.port = port;
    }

    public List<GuildMember> execute(long characterId) {
        return port.findMembershipOf(characterId)
                .map(member -> port.listMembers(member.guildId()))
                .orElse(List.of());
    }
}
