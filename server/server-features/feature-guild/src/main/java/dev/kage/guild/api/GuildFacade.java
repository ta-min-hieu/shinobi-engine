package dev.kage.guild.api;

import java.util.List;
import java.util.Optional;

import dev.kage.guild.application.ApproveGuildJoinRequestUseCase;
import dev.kage.guild.application.CreateGuildUseCase;
import dev.kage.guild.application.DisbandGuildUseCase;
import dev.kage.guild.application.GuildUseCases;
import dev.kage.guild.application.KickGuildMemberUseCase;
import dev.kage.guild.application.LeaveGuildUseCase;
import dev.kage.guild.application.RejectGuildJoinRequestUseCase;
import dev.kage.guild.domain.GuildRole;

/**
 * API công khai của {@code feature-guild} — module khác (server-gateway) CHỈ gọi qua đây, không
 * bao giờ đọc bảng {@code guild}/{@code guild_member}/{@code guild_join_request} trực tiếp (Điều
 * 24). Tên nhân vật/trạng thái online KHÔNG thuộc facade này — cùng quyết định feature-friend,
 * gateway tự resolve tên qua {@code CharacterFacade} và online qua {@code ConnectedCharacterRegistry}.
 */
public final class GuildFacade {

    private final GuildUseCases useCases;

    public GuildFacade(GuildUseCases useCases) {
        this.useCases = useCases;
    }

    public CreateResult createGuild(long characterId, String name) {
        CreateGuildUseCase.Result result = useCases.createGuildUseCase().execute(characterId, name);
        CreateOutcome outcome =
                switch (result.outcome()) {
                    case OK -> CreateOutcome.OK;
                    case ALREADY_IN_GUILD -> CreateOutcome.ALREADY_IN_GUILD;
                    case NAME_TAKEN -> CreateOutcome.NAME_TAKEN;
                    case INVALID_NAME -> CreateOutcome.INVALID_NAME;
                };
        Long guildId = result.guild() == null ? null : result.guild().id();
        return new CreateResult(outcome, guildId);
    }

    public SendJoinRequestResult sendJoinRequest(long characterId, String guildName) {
        return switch (useCases.sendGuildJoinRequestUseCase().execute(characterId, guildName)) {
            case OK -> SendJoinRequestResult.OK;
            case ALREADY_IN_GUILD -> SendJoinRequestResult.ALREADY_IN_GUILD;
            case GUILD_NOT_FOUND -> SendJoinRequestResult.GUILD_NOT_FOUND;
            case REQUEST_ALREADY_PENDING -> SendJoinRequestResult.REQUEST_ALREADY_PENDING;
        };
    }

    public ApproveResult approveJoinRequest(long requestId, long leaderCharacterId) {
        return switch (useCases.approveGuildJoinRequestUseCase().execute(requestId, leaderCharacterId)) {
            case ApproveGuildJoinRequestUseCase.Result.OK -> ApproveResult.OK;
            case ApproveGuildJoinRequestUseCase.Result.NOT_FOUND -> ApproveResult.NOT_FOUND;
            case ApproveGuildJoinRequestUseCase.Result.TARGET_ALREADY_IN_GUILD -> ApproveResult.TARGET_ALREADY_IN_GUILD;
            case ApproveGuildJoinRequestUseCase.Result.GUILD_FULL -> ApproveResult.GUILD_FULL;
        };
    }

    public RejectResult rejectJoinRequest(long requestId, long leaderCharacterId) {
        return useCases.rejectGuildJoinRequestUseCase().execute(requestId, leaderCharacterId)
                        == RejectGuildJoinRequestUseCase.Result.OK
                ? RejectResult.OK
                : RejectResult.NOT_FOUND;
    }

    public LeaveResult leaveGuild(long characterId) {
        return switch (useCases.leaveGuildUseCase().execute(characterId)) {
            case LeaveGuildUseCase.Result.OK -> LeaveResult.OK;
            case LeaveGuildUseCase.Result.NOT_IN_GUILD -> LeaveResult.NOT_IN_GUILD;
            case LeaveGuildUseCase.Result.LEADER_CANNOT_LEAVE -> LeaveResult.LEADER_CANNOT_LEAVE;
        };
    }

    public DisbandResult disbandGuild(long characterId) {
        return useCases.disbandGuildUseCase().execute(characterId) == DisbandGuildUseCase.Result.OK
                ? DisbandResult.OK
                : DisbandResult.NOT_LEADER;
    }

    public KickResult kickMember(long leaderCharacterId, long targetCharacterId) {
        return switch (useCases.kickGuildMemberUseCase().execute(leaderCharacterId, targetCharacterId)) {
            case KickGuildMemberUseCase.Result.OK -> KickResult.OK;
            case KickGuildMemberUseCase.Result.CANNOT_KICK_SELF -> KickResult.CANNOT_KICK_SELF;
            case KickGuildMemberUseCase.Result.NOT_FOUND -> KickResult.NOT_FOUND;
        };
    }

    /** Thành viên bang của {@code characterId} — rỗng nếu chưa vào bang nào. */
    public List<MemberView> listMembers(long characterId) {
        return useCases.listGuildMembersUseCase().execute(characterId).stream()
                .map(m -> new MemberView(m.characterId(), m.role() == GuildRole.LEADER))
                .toList();
    }

    /** Đơn xin gia nhập đang chờ {@code leaderCharacterId} duyệt — rỗng nếu không phải leader. */
    public List<PendingJoinRequestView> listPendingJoinRequests(long leaderCharacterId) {
        return useCases.listPendingGuildJoinRequestsUseCase().execute(leaderCharacterId).stream()
                .map(r -> new PendingJoinRequestView(r.id(), r.characterId()))
                .toList();
    }

    public Optional<MyGuildView> findMyGuild(long characterId) {
        return useCases.findMyGuildUseCase()
                .execute(characterId)
                .map(r -> new MyGuildView(r.guildId(), r.guildName(), r.myRole() == GuildRole.LEADER));
    }

    public enum CreateOutcome {
        OK,
        ALREADY_IN_GUILD,
        NAME_TAKEN,
        /**
         * Tên sai luật tên bang (= luật tên nhân vật, 2026-09-29). Gateway trả {@code ErrorResponse}
         * {@code error.guild_name_invalid} — KHÔNG có giá trị tương ứng trong guild.proto.
         */
        INVALID_NAME
    }

    public enum SendJoinRequestResult {
        OK,
        ALREADY_IN_GUILD,
        GUILD_NOT_FOUND,
        REQUEST_ALREADY_PENDING
    }

    public enum ApproveResult {
        OK,
        NOT_FOUND,
        TARGET_ALREADY_IN_GUILD,
        GUILD_FULL
    }

    public enum RejectResult {
        OK,
        NOT_FOUND
    }

    public enum LeaveResult {
        OK,
        NOT_IN_GUILD,
        LEADER_CANNOT_LEAVE
    }

    public enum DisbandResult {
        OK,
        NOT_LEADER
    }

    public enum KickResult {
        OK,
        CANNOT_KICK_SELF,
        NOT_FOUND
    }

    public record CreateResult(CreateOutcome outcome, Long guildId) {}

    public record MemberView(long characterId, boolean isLeader) {}

    public record PendingJoinRequestView(long requestId, long characterId) {}

    public record MyGuildView(long guildId, String guildName, boolean isLeader) {}
}
