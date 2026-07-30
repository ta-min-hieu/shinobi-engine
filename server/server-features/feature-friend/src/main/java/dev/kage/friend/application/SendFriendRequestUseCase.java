package dev.kage.friend.application;

import java.util.Optional;

import dev.kage.friend.domain.DuplicateFriendRequestException;
import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;

/** Gửi lời mời kết bạn — kiểm tự-mời/đã-bạn/đã-có-lời-mời TRƯỚC khi chạm DB. */
public final class SendFriendRequestUseCase {

    private final FriendPort port;

    public SendFriendRequestUseCase(FriendPort port) {
        this.port = port;
    }

    public Result execute(long requesterId, long addresseeId) {
        if (requesterId == addresseeId) {
            return Result.CANNOT_FRIEND_SELF;
        }
        Optional<Friendship> existing = port.findBetween(requesterId, addresseeId);
        if (existing.isPresent()) {
            return existing.get().status() == FriendshipStatus.ACCEPTED
                    ? Result.ALREADY_FRIENDS
                    : Result.REQUEST_ALREADY_PENDING;
        }
        try {
            port.sendRequest(requesterId, addresseeId);
        } catch (DuplicateFriendRequestException e) {
            return Result.REQUEST_ALREADY_PENDING;
        }
        return Result.OK;
    }

    public enum Result {
        OK,
        CANNOT_FRIEND_SELF,
        ALREADY_FRIENDS,
        REQUEST_ALREADY_PENDING
    }
}
