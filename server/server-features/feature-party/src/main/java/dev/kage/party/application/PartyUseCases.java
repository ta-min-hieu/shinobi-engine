package dev.kage.party.application;

/**
 * Gộp toàn bộ UseCase mà {@link dev.kage.party.api.PartyFacade} cần — tránh constructor Facade
 * vượt 4 tham số (Checkstyle {@code ParameterNumber}), cùng tinh thần {@code GuildUseCases}.
 */
public record PartyUseCases(
        CreatePartyUseCase createPartyUseCase,
        InvitePartyMemberUseCase invitePartyMemberUseCase,
        AcceptPartyInviteUseCase acceptPartyInviteUseCase,
        RejectPartyInviteUseCase rejectPartyInviteUseCase,
        LeavePartyUseCase leavePartyUseCase,
        KickPartyMemberUseCase kickPartyMemberUseCase,
        ListPartyMembersUseCase listPartyMembersUseCase,
        ListPendingPartyInvitesUseCase listPendingPartyInvitesUseCase,
        FindMyPartyUseCase findMyPartyUseCase,
        HandOverPartyLeadershipUseCase handOverPartyLeadershipUseCase) {}
