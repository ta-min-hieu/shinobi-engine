package dev.kage.friend.application;

/**
 * Gộp 6 UseCase mà {@link dev.kage.friend.api.FriendFacade} cần — tránh constructor vượt 4 tham số
 * (Checkstyle {@code ParameterNumber}), cùng tinh thần {@code ShopPorts}/{@code EquipmentUseCases}.
 */
public record FriendUseCases(
        SendFriendRequestUseCase sendFriendRequestUseCase,
        AcceptFriendRequestUseCase acceptFriendRequestUseCase,
        RejectFriendRequestUseCase rejectFriendRequestUseCase,
        RemoveFriendUseCase removeFriendUseCase,
        ListFriendsUseCase listFriendsUseCase,
        ListPendingFriendRequestsUseCase listPendingFriendRequestsUseCase) {
}
