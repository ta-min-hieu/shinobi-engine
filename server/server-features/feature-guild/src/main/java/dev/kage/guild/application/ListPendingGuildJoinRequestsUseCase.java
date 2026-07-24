package dev.kage.guild.application;

import dev.kage.guild.domain.GuildJoinRequest;
import dev.kage.guild.domain.GuildPort;
import dev.kage.guild.domain.GuildRole;
import java.util.List;

/** Đơn xin gia nhập đang chờ leader duyệt — rỗng nếu {@code leaderCharacterId} không phải leader. */
public final class ListPendingGuildJoinRequestsUseCase {

    private final GuildPort port;

    public ListPendingGuildJoinRequestsUseCase(GuildPort port) {
        this.port = port;
    }

    public List<GuildJoinRequest> execute(long leaderCharacterId) {
        return port.findMembershipOf(leaderCharacterId)
                .filter(member -> member.role() == GuildRole.LEADER)
                .map(member -> port.listPendingRequests(member.guildId()))
                .orElse(List.of());
    }
}
