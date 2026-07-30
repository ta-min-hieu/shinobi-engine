package dev.kage.friend.application;

import dev.kage.friend.domain.FriendPort;

/** Chấp nhận lời mời kết bạn. */
public final class AcceptFriendRequestUseCase {

    private final FriendPort port;

    public AcceptFriendRequestUseCase(FriendPort port) {
        this.port = port;
    }

    public Result execute(long friendshipId, long addresseeId) {
        return port.accept(friendshipId, addresseeId) ? Result.OK : Result.NOT_FOUND;
    }

    public enum Result {
        OK,
        NOT_FOUND
    }
}
