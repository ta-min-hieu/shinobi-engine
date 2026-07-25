package dev.kage.guild.application;

import dev.kage.guild.domain.DuplicateGuildJoinRequestException;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildPort;
import java.util.Optional;

/**
 * Xin gia nhập bang theo tên — leader duyệt/từ chối sau (quyết định cùng chủ dự án 2026-07-23,
 * khác feature-friend là mời trực tiếp). Không chặn 1 nhân vật có đơn PENDING ở bang này trong
 * lúc TỰ TẠO bang khác — xem {@link GuildPort.ApproveResult#TARGET_ALREADY_IN_GUILD}.
 */
public final class SendGuildJoinRequestUseCase {

    private final GuildPort port;

    public SendGuildJoinRequestUseCase(GuildPort port) {
        this.port = port;
    }

    public Result execute(long characterId, String guildName) {
        if (port.findMembershipOf(characterId).isPresent()) {
            return Result.ALREADY_IN_GUILD;
        }
        Optional<Guild> guild = port.findByName(guildName);
        if (guild.isEmpty()) {
            return Result.GUILD_NOT_FOUND;
        }
        if (port.findPendingRequestOfCharacter(characterId).isPresent()) {
            return Result.REQUEST_ALREADY_PENDING;
        }
        try {
            port.sendJoinRequest(guild.get().id(), characterId);
        } catch (DuplicateGuildJoinRequestException e) {
            return Result.REQUEST_ALREADY_PENDING;
        }
        return Result.OK;
    }

    public enum Result { OK, ALREADY_IN_GUILD, GUILD_NOT_FOUND, REQUEST_ALREADY_PENDING }
}
