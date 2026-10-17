package dev.kage.friend.application;

import java.util.Optional;

import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;

/** Từ chối lời mời kết bạn — xoá dòng PENDING. */
public final class RejectFriendRequestUseCase {

    private final FriendPort port;
    private final FriendRequestCooldowns cooldowns;

    public RejectFriendRequestUseCase(FriendPort port) {
        this(port, new FriendRequestCooldowns());
    }

    /** {@code cooldowns} dùng chung với {@link SendFriendRequestUseCase} — xem {@link FriendRequestCooldowns}. */
    public RejectFriendRequestUseCase(FriendPort port, FriendRequestCooldowns cooldowns) {
        this.port = port;
        this.cooldowns = cooldowns;
    }

    public Result execute(long friendshipId, long addresseeId) {
        // Đọc người mời TRƯỚC khi xoá — sau đó dòng không còn để tra.
        Optional<Long> requesterId = port.listPendingIncoming(addresseeId).stream()
                .filter(pending -> pending.id() == friendshipId)
                .map(Friendship::requesterId)
                .findFirst();
        if (!port.reject(friendshipId, addresseeId)) {
            return Result.NOT_FOUND;
        }
        requesterId.ifPresent(requester -> cooldowns.recordRejection(requester, addresseeId));
        return Result.OK;
    }

    public enum Result {
        OK,
        NOT_FOUND
    }
}
