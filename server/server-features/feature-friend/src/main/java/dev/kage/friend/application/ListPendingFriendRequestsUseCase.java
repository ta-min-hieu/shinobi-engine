package dev.kage.friend.application;

import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import java.util.List;

/** Lời mời kết bạn đang chờ {@code characterId} chấp nhận/từ chối. */
public final class ListPendingFriendRequestsUseCase {

    private final FriendPort port;

    public ListPendingFriendRequestsUseCase(FriendPort port) {
        this.port = port;
    }

    public List<Friendship> execute(long characterId) {
        return port.listPendingIncoming(characterId);
    }
}
