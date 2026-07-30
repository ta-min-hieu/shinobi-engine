package dev.kage.friend.application;

import dev.kage.friend.domain.FriendPort;

/** Từ chối lời mời kết bạn — xoá dòng PENDING. */
public final class RejectFriendRequestUseCase {

    private final FriendPort port;

    public RejectFriendRequestUseCase(FriendPort port) {
        this.port = port;
    }

    public Result execute(long friendshipId, long addresseeId) {
        return port.reject(friendshipId, addresseeId) ? Result.OK : Result.NOT_FOUND;
    }

    public enum Result {
        OK,
        NOT_FOUND
    }
}
