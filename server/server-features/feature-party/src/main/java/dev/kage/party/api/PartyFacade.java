package dev.kage.party.api;

import java.util.List;
import java.util.Optional;

import dev.kage.party.application.AcceptPartyInviteUseCase;
import dev.kage.party.application.CreatePartyUseCase;
import dev.kage.party.application.KickPartyMemberUseCase;
import dev.kage.party.application.LeavePartyUseCase;
import dev.kage.party.application.PartyUseCases;
import dev.kage.party.application.RejectPartyInviteUseCase;
import dev.kage.party.domain.PartyRole;

/**
 * API công khai của {@code feature-party} — module khác (server-gateway) CHỈ gọi qua đây, không
 * bao giờ đọc bảng {@code party}/{@code party_member}/{@code party_invite} trực tiếp (Điều 24).
 * Tên nhân vật/trạng thái online KHÔNG thuộc facade này — cùng quyết định feature-friend/guild.
 */
public final class PartyFacade {

    private final PartyUseCases useCases;

    /** Dựng ở composition root. */
    public PartyFacade(PartyUseCases useCases) {
        this.useCases = useCases;
    }

    /** Lập nhóm — người lập thành trưởng nhóm. */
    public CreateResult createParty(long characterId) {
        CreatePartyUseCase.Result result = useCases.createPartyUseCase().execute(characterId);
        CreateOutcome outcome = result.outcome() == dev.kage.party.application.CreatePartyUseCase.Outcome.OK
                ? CreateOutcome.OK
                : CreateOutcome.ALREADY_IN_PARTY;
        Long partyId = result.party() == null ? null : result.party().id();
        return new CreateResult(outcome, partyId);
    }

    /** Trưởng nhóm mời {@code targetCharacterId}. */
    public InviteResult invite(long leaderCharacterId, long targetCharacterId) {
        return switch (useCases.invitePartyMemberUseCase().execute(leaderCharacterId, targetCharacterId)) {
            case OK -> InviteResult.OK;
            case NOT_LEADER -> InviteResult.NOT_LEADER;
            case CANNOT_INVITE_SELF -> InviteResult.CANNOT_INVITE_SELF;
            case TARGET_ALREADY_IN_PARTY -> InviteResult.TARGET_ALREADY_IN_PARTY;
            case PARTY_FULL -> InviteResult.PARTY_FULL;
            case INVITE_ALREADY_PENDING -> InviteResult.INVITE_ALREADY_PENDING;
        };
    }

    /** Người được mời chấp nhận lời mời {@code inviteId}. */
    public AcceptResult acceptInvite(long inviteId, long characterId) {
        return switch (useCases.acceptPartyInviteUseCase().execute(inviteId, characterId)) {
            case AcceptPartyInviteUseCase.Result.OK -> AcceptResult.OK;
            case AcceptPartyInviteUseCase.Result.NOT_FOUND -> AcceptResult.NOT_FOUND;
            case AcceptPartyInviteUseCase.Result.ALREADY_IN_PARTY -> AcceptResult.ALREADY_IN_PARTY;
            case AcceptPartyInviteUseCase.Result.PARTY_FULL -> AcceptResult.PARTY_FULL;
        };
    }

    /** Người được mời từ chối lời mời {@code inviteId}. */
    public RejectResult rejectInvite(long inviteId, long characterId) {
        return useCases.rejectPartyInviteUseCase().execute(inviteId, characterId) == RejectPartyInviteUseCase.Result.OK
                ? RejectResult.OK
                : RejectResult.NOT_FOUND;
    }

    /** Rời nhóm. */
    public LeaveResult leaveParty(long characterId) {
        return useCases.leavePartyUseCase().execute(characterId) == LeavePartyUseCase.Result.OK
                ? LeaveResult.OK
                : LeaveResult.NOT_IN_PARTY;
    }

    /** Trưởng nhóm đuổi thành viên {@code targetCharacterId}. */
    public KickResult kickMember(long leaderCharacterId, long targetCharacterId) {
        return switch (useCases.kickPartyMemberUseCase().execute(leaderCharacterId, targetCharacterId)) {
            case KickPartyMemberUseCase.Result.OK -> KickResult.OK;
            case KickPartyMemberUseCase.Result.CANNOT_KICK_SELF -> KickResult.CANNOT_KICK_SELF;
            case KickPartyMemberUseCase.Result.NOT_FOUND -> KickResult.NOT_FOUND;
        };
    }

    /** Thành viên party của {@code characterId} — rỗng nếu chưa vào party nào. */
    public List<MemberView> listMembers(long characterId) {
        return useCases.listPartyMembersUseCase().execute(characterId).stream()
                .map(m -> new MemberView(m.characterId(), m.role() == PartyRole.LEADER))
                .toList();
    }

    /** Lời mời đang chờ {@code characterId} (vai trò invitee) chấp nhận/từ chối. */
    public List<PendingInviteView> listPendingInvites(long characterId) {
        return useCases.listPendingPartyInvitesUseCase().execute(characterId).stream()
                .map(i -> new PendingInviteView(i.id(), i.partyId()))
                .toList();
    }

    /**
     * Trưởng nhóm vừa mất kết nối: trao quyền cho thành viên vào sớm nhất đang online — xem
     * {@code HandOverPartyLeadershipUseCase}. Chạm DB (blocking).
     *
     * @return trưởng nhóm mới, hoặc rỗng nếu không có gì đổi
     */
    public Optional<Long> handOverLeadership(long leaderCharacterId, java.util.function.LongPredicate isOnline) {
        return useCases.handOverPartyLeadershipUseCase().execute(leaderCharacterId, isOnline);
    }

    /** Nhóm của {@code characterId} — rỗng nếu chưa vào nhóm nào. */
    public Optional<MyPartyView> findMyParty(long characterId) {
        return useCases.findMyPartyUseCase()
                .execute(characterId)
                .map(r -> new MyPartyView(r.partyId(), r.myRole() == PartyRole.LEADER));
    }

    /** Kết quả lập nhóm. */
    public enum CreateOutcome {
        OK,
        ALREADY_IN_PARTY
    }

    /** Kết quả mời vào nhóm. */
    public enum InviteResult {
        OK,
        NOT_LEADER,
        CANNOT_INVITE_SELF,
        TARGET_ALREADY_IN_PARTY,
        PARTY_FULL,
        INVITE_ALREADY_PENDING
    }

    /** Kết quả chấp nhận lời mời. */
    public enum AcceptResult {
        OK,
        NOT_FOUND,
        ALREADY_IN_PARTY,
        PARTY_FULL
    }

    /** Kết quả từ chối lời mời. */
    public enum RejectResult {
        OK,
        NOT_FOUND
    }

    /** Kết quả rời nhóm. */
    public enum LeaveResult {
        OK,
        NOT_IN_PARTY
    }

    /** Kết quả đuổi thành viên. */
    public enum KickResult {
        OK,
        CANNOT_KICK_SELF,
        NOT_FOUND
    }

    /** Kết quả lập nhóm — {@code partyId} là {@code null} khi {@code outcome != OK}. */
    public record CreateResult(CreateOutcome outcome, Long partyId) {}

    /** Một thành viên nhóm. */
    public record MemberView(long characterId, boolean isLeader) {}

    /** Một lời mời vào nhóm đang chờ trả lời. */
    public record PendingInviteView(long inviteId, long partyId) {}

    /** Nhóm của người hỏi và vai trò của họ trong đó. */
    public record MyPartyView(long partyId, boolean isLeader) {}
}
