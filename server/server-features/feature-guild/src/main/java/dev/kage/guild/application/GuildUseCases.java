package dev.kage.guild.application;

/**
 * Gộp toàn bộ UseCase mà {@link dev.kage.guild.api.GuildFacade} cần — tránh constructor Facade
 * vượt 4 tham số (Checkstyle {@code ParameterNumber}), cùng tinh thần {@code FriendUseCases}.
 * Record header (không phải constructor tường minh) nên KHÔNG bị {@code ParameterNumber} tính,
 * xem tiền lệ {@code FriendUseCases} (6 thành phần, đã build xanh).
 */
public record GuildUseCases(
        CreateGuildUseCase createGuildUseCase,
        SendGuildJoinRequestUseCase sendGuildJoinRequestUseCase,
        ApproveGuildJoinRequestUseCase approveGuildJoinRequestUseCase,
        RejectGuildJoinRequestUseCase rejectGuildJoinRequestUseCase,
        LeaveGuildUseCase leaveGuildUseCase,
        DisbandGuildUseCase disbandGuildUseCase,
        KickGuildMemberUseCase kickGuildMemberUseCase,
        ListGuildMembersUseCase listGuildMembersUseCase,
        ListPendingGuildJoinRequestsUseCase listPendingGuildJoinRequestsUseCase,
        FindMyGuildUseCase findMyGuildUseCase) {}
