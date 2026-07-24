package dev.kage.guild.application;

import dev.kage.guild.domain.GuildPort;

/** Leader từ chối đơn xin gia nhập — xoá dòng, không giữ lịch sử (cùng quyết định feature-friend). */
public final class RejectGuildJoinRequestUseCase {

    private final GuildPort port;

    public RejectGuildJoinRequestUseCase(GuildPort port) {
        this.port = port;
    }

    public Result execute(long requestId, long leaderCharacterId) {
        return port.rejectJoinRequest(requestId, leaderCharacterId) ? Result.OK : Result.NOT_FOUND;
    }

    public enum Result { OK, NOT_FOUND }
}
