package dev.kage.friend.application;

import java.util.Optional;

import dev.kage.friend.domain.FriendListLimit;
import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;

/** Chấp nhận lời mời kết bạn. */
public final class AcceptFriendRequestUseCase {

    private final FriendPort port;

    public AcceptFriendRequestUseCase(FriendPort port) {
        this.port = port;
    }

    public Result execute(long friendshipId, long addresseeId) {
        // Trần danh sách bạn bè (2026-10-11) kiểm ở CẢ HAI phía: chấp nhận là thêm một người vào danh
        // sách của người chấp nhận lẫn của người đã mời.
        if (FriendListLimit.isFull(port.listAccepted(addresseeId).size())) {
            return Result.FRIEND_LIST_FULL;
        }
        Optional<Friendship> request = port.listPendingIncoming(addresseeId).stream()
                .filter(pending -> pending.id() == friendshipId)
                .findFirst();
        if (request.isPresent()
                && FriendListLimit.isFull(
                        port.listAccepted(request.get().requesterId()).size())) {
            return Result.REQUESTER_FRIEND_LIST_FULL;
        }
        return port.accept(friendshipId, addresseeId) ? Result.OK : Result.NOT_FOUND;
    }

    public enum Result {
        OK,
        NOT_FOUND,
        /** Danh sách của chính người chấp nhận đã đầy. */
        FRIEND_LIST_FULL,
        /** Danh sách của người ĐÃ MỜI đã đầy — lời mời còn nguyên, chấp nhận lại được khi họ có chỗ. */
        REQUESTER_FRIEND_LIST_FULL
    }
}
