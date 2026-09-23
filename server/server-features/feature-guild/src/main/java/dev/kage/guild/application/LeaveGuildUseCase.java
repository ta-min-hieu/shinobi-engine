package dev.kage.guild.application;

import java.util.Optional;

import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;
import dev.kage.guild.domain.GuildRole;

/** Thành viên MEMBER tự rời bang — LEADER không được rời, phải {@link DisbandGuildUseCase}. */
public final class LeaveGuildUseCase {

    private final GuildPort port;

    public LeaveGuildUseCase(GuildPort port) {
        this.port = port;
    }

    public Result execute(long characterId) {
        Optional<GuildMember> membership = port.findMembershipOf(characterId);
        if (membership.isEmpty()) {
            return Result.NOT_IN_GUILD;
        }
        if (membership.get().role() == GuildRole.LEADER) {
            return Result.LEADER_CANNOT_LEAVE;
        }
        // findMembershipOf rồi mới leaveGuild không atomic — nếu leader KICK đúng nhân vật này
        // ngay giữa 2 lời gọi (virtual thread khác, không tuần tự hoá theo guild), leaveGuild xoá 0
        // dòng (đã bị kick xoá trước). Phải đọc lại kết quả DELETE thật, không được mặc định OK.
        return port.leaveGuild(characterId) ? Result.OK : Result.NOT_IN_GUILD;
    }

    public enum Result {
        OK,
        NOT_IN_GUILD,
        LEADER_CANNOT_LEAVE
    }
}
