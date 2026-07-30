package dev.kage.guild.application;

import dev.kage.guild.domain.GuildPort;

/** Leader duyệt đơn xin gia nhập — thêm thành viên MỚI + xoá đơn, nguyên tử ở {@link GuildPort}. */
public final class ApproveGuildJoinRequestUseCase {

    private final GuildPort port;

    public ApproveGuildJoinRequestUseCase(GuildPort port) {
        this.port = port;
    }

    public Result execute(long requestId, long leaderCharacterId) {
        return switch (port.approveJoinRequest(requestId, leaderCharacterId)) {
            case OK -> Result.OK;
            case NOT_FOUND -> Result.NOT_FOUND;
            case TARGET_ALREADY_IN_GUILD -> Result.TARGET_ALREADY_IN_GUILD;
        };
    }

    public enum Result {
        OK,
        NOT_FOUND,
        TARGET_ALREADY_IN_GUILD
    }
}
